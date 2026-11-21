// security/application/internal/ArmouryService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.security.domain.model.ArmouryLog;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.ArmouryLogRepository;
import za.co.handyflow.platform.security.domain.repository.ArmouryRepository;
import za.co.handyflow.platform.security.domain.repository.AuditEventRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * ArmouryService — firearm register CRUD and the witnessed issue/return workflow.
 *
 * Compliance gates enforced on every issue:
 *   1. Firearm license not expired
 *   2. Firearm status is IN_ARMOURY (not already issued/lost/decommissioned)
 *   3. Receiving guard has a valid (non-expired) firearm competency certificate
 *   4. Witness is a different guard from the one receiving the firearm
 *   5. Witness exists and belongs to the same tenant
 *
 * DECISION (product owner, Armoury-in-Shield question): Armoury does
 * NOT belong in the normal guard-facing Shield mobile app for V1,
 * despite an earlier architecture doc listing it under Shield's own
 * navigation. The product owner's own reasoning, verbatim: "The
 * current authority model is telling you something important... I
 * would not weaken that authorization model simply to make the mobile
 * navigation match an architecture document. That would be backwards."
 * issue()/returnFirearm() below require SECURITY_MANAGE — confirmed no
 * guard token will ever carry it, by design (GuardAuthService issues
 * only SECURITY_GUARD/SECURITY_SCAN) — and that stays exactly as it
 * is; this comment exists so a future pass doesn't "fix" the mismatch
 * by loosening this service's own authority checks to match a
 * navigation mockup instead of the other way around.
 * <p>
 * If mobile armoury access is ever genuinely wanted: the product
 * owner's own sketch is a person's Guard record carrying a separate,
 * explicit ARMOURY_OPERATOR responsibility — "being a guard should
 * never automatically grant armoury authority... someone may be Guard
 * + Armoury Operator, but that must be explicitly authorized" — not a
 * new elevated guard-login tier, and not a blanket loosening of
 * SECURITY_MANAGE here. Not built — a future decision, not a gap in
 * this pass.
 * <p>
 * For V1: Armoury stays reachable only through this controller's own
 * tenant-JWT surface (the ordinary Security web app) — there is no
 * guard-facing armoury controller anywhere in this codebase, confirmed
 * directly, not assumed.
 *
 * These are hard blocks, not advisory warnings (unlike GuardScreeningService's
 * checkScreeningGate) — firearm issue is the one place in this module where
 * the Firearms Control Act makes "supervisor can override" not a safe default.
 *
 * WHY does issue()/returnFirearm() take both a witness UUID and validate it
 * server-side rather than trusting the client?
 * The witness step has compliance weight — if the witness check could be
 * spoofed by sending any UUID, the two-person verification becomes theatre.
 * Validating the witness is an active guard in this tenant (not the issuing
 * guard themselves) is the minimum bar for this to mean anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArmouryService {

    private final ArmouryRepository    armouryRepository;
    private final ArmouryLogRepository logRepository;
    private final GuardRepository      guardRepository;
    // FIX: closes the confirmed AuditEvent coverage gap for Armoury —
    // ArmouryLog already records the detailed chain-of-custody entry
    // (witness, condition notes) that drives isAvailableForIssue() and
    // similar business logic; AuditEvent is the separate, generic,
    // cross-entity activity trail (the same table ShiftService already
    // writes to) — someone auditing "everything actor Y did" or
    // "everything that happened in this tenant on date X" should see
    // firearm issue/return there too, not only by knowing to check
    // ArmouryLog specifically. Genuinely additive, not redundant.
    private final AuditEventRepository auditRepository;

    // Optional collaborators, field-injected so existing constructor callers keep working.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SecurityAlertNotifier alerts;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.domain.repository.ShiftRepository shiftRepository;
    @org.springframework.beans.factory.annotation.Value("${handyflow.security.armoury.require-shift:false}")
    private boolean requireShift;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository competencyRepository;
    @org.springframework.beans.factory.annotation.Value("${handyflow.security.armoury.strict-competency:false}")
    private boolean strictCompetency;

    // ── Register CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public ArmouryResponse register(TenantId tenantId, RegisterFirearmRequest req) {
        if (armouryRepository.existsBySerial(tenantId, req.firearmSerial())) {
            throw new HandyFlowException(
                    "A firearm with serial " + req.firearmSerial() + " is already registered",
                    HttpStatus.CONFLICT, "DUPLICATE_SERIAL");
        }
        Armoury firearm = Armoury.register(
                tenantId, req.firearmSerial(), req.firearmType(), req.makeModel(),
                req.sapsLicenseNumber(), req.licenseIssuedAt(), req.licenseExpiry(),
                req.notes());
        armouryRepository.save(firearm);

        log.info("[Security] Firearm registered serial={} tenant={}",
                req.firearmSerial(), tenantId.getValue());
        return toResponse(firearm, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<ArmouryResponse> getAll(TenantId tenantId, Pageable pageable) {
        return armouryRepository.findAllActive(tenantId, pageable)
                .map(a -> toResponse(a, tenantId));
    }

    @Transactional(readOnly = true)
    public ArmouryResponse getById(TenantId tenantId, UUID id) {
        Armoury firearm = findActive(tenantId, id);
        return toResponse(firearm, tenantId);
    }

    @Transactional
    public ArmouryResponse updateLicense(TenantId tenantId, UUID id,
                                         UpdateFirearmLicenseRequest req) {
        Armoury firearm = findActive(tenantId, id);
        firearm.updateLicense(req.sapsLicenseNumber(), req.licenseIssuedAt(), req.licenseExpiry());
        armouryRepository.save(firearm);
        return toResponse(firearm, tenantId);
    }

    @Transactional
    public ArmouryResponse recordService(TenantId tenantId, UUID id,
                                         ServiceFirearmRequest req) {
        Armoury firearm = findActive(tenantId, id);
        firearm.recordService(req.serviceDate(), req.nextDueDate());
        armouryRepository.save(firearm);
        return toResponse(firearm, tenantId);
    }

    @Transactional
    public ArmouryResponse reportLost(TenantId tenantId, UUID id,
                                      ReportLostFirearmRequest req, UUID actorId) {
        Armoury firearm = findActive(tenantId, id);
        if (firearm.getStatus() == Armoury.ArmouryStatus.LOST) {
            throw new HandyFlowException("This firearm is already reported lost",
                    HttpStatus.CONFLICT, "ALREADY_LOST");
        }
        String caseNo = normaliseCase(req.sapsCaseNumber());
        boolean hadHolder = firearm.getAssignedGuardId() != null;
        java.time.Instant now = java.time.Instant.now();
        firearm.reportLost(req.notes(), caseNo, now, LostReportRules.dueAt(now));
        armouryRepository.save(firearm);
        writeAudit(tenantId, actorId, id, "REPORTED_LOST",
                "{\"sapsCaseNumber\":" + (caseNo == null ? "null" : "true") + ",\"hadHolder\":" + hadHolder + "}");
        if (alerts != null) alerts.firearmLost(tenantId, id, firearm.getFirearmSerial(), hadHolder);

        log.warn("[Security] Firearm reported LOST serial={} tenant={}",
                firearm.getFirearmSerial(), tenantId.getValue());
        return toResponse(firearm, tenantId);
    }

    /** Records the SAPS case (CAS) number once the loss has been reported to the police. */
    @Transactional
    public ArmouryResponse recordSapsCase(TenantId tenantId, UUID id, RecordSapsCaseRequest req, UUID actorId) {
        Armoury firearm = findActive(tenantId, id);
        if (firearm.getStatus() != Armoury.ArmouryStatus.LOST) {
            throw new HandyFlowException("Only a firearm reported lost has a SAPS case",
                    HttpStatus.CONFLICT, "NOT_LOST");
        }
        firearm.recordSapsCase(normaliseCase(req.caseNumber()), java.time.Instant.now());
        armouryRepository.save(firearm);
        writeAudit(tenantId, actorId, id, "SAPS_CASE_RECORDED", "{}");
        return toResponse(firearm, tenantId);
    }

    private static String normaliseCase(String raw) {
        try {
            return LostReportRules.normaliseCase(raw);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
    }

    @Transactional
    public ArmouryResponse decommission(TenantId tenantId, UUID id,
                                        DecommissionFirearmRequest req) {
        Armoury firearm = findActive(tenantId, id);
        firearm.decommission(req.reason());
        armouryRepository.save(firearm);
        return toResponse(firearm, tenantId);
    }

    // ── Guard Firearm Competency ───────────────────────────────────────────────

    @Transactional
    public void setGuardCompetency(TenantId tenantId, UUID guardId,
                                   SetFirearmCompetencyRequest req) {
        Guard guard = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
        guard.setFirearmCompetency(req.competencyNumber(), req.expiry());
        guardRepository.save(guard);

        log.info("[Security] Firearm competency set guardId={} expiry={}",
                guardId, req.expiry());
    }

    // ── Witnessed Issue / Return ───────────────────────────────────────────────

    /**
     * Issues a firearm to a guard, with mandatory two-person witness.
     *
     * Hard-blocks (HandyFlowException, not advisory) if:
     *   - firearm license expired
     *   - firearm not currently IN_ARMOURY
     *   - receiving guard's firearm competency expired or never set
     *   - witness is the same guard, doesn't exist, or isn't ACTIVE
     */
    @Transactional
    public ArmouryResponse issue(TenantId tenantId, UUID armouryId, IssueFirearmRequest req, UUID actorId) {
        Armoury firearm = findActive(tenantId, armouryId);

        if (firearm.isLicenseExpired()) {
            throw new HandyFlowException(
                    "Firearm " + firearm.getFirearmSerial() + " has an expired SAPS license ("
                            + firearm.getLicenseExpiry() + ") — cannot issue",
                    HttpStatus.CONFLICT, "LICENSE_EXPIRED");
        }
        if (!firearm.isAvailableForIssue()) {
            throw new HandyFlowException(
                    "Firearm " + firearm.getFirearmSerial() + " is " + firearm.getStatus()
                            + " and cannot be issued",
                    HttpStatus.CONFLICT, "FIREARM_NOT_AVAILABLE");
        }

        Guard receivingGuard = guardRepository.findActiveById(tenantId, req.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard", req.guardId().toString()));

        if (!receivingGuard.isSchedulable()) {
            throw new HandyFlowException(
                    "Guard " + receivingGuard.getFullName() + " is " + receivingGuard.getStatus()
                            + " and cannot be issued a firearm",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_SCHEDULABLE");
        }
        checkCompetency(tenantId, receivingGuard, firearm);

        String psiraProblem = IssueChecks.psira(java.time.LocalDate.now(),
                receivingGuard.getPsiraNumber(), receivingGuard.getPsiraExpiryDate());
        if (psiraProblem != null) {
            throw new HandyFlowException(
                    "Guard " + receivingGuard.getFullName() + (psiraProblem.equals("PSIRA_EXPIRED")
                            ? " has an expired PSiRA registration" : " has no PSiRA registration on file")
                            + " — cannot issue a firearm",
                    HttpStatus.FORBIDDEN, psiraProblem);
        }
        checkShift(tenantId, req);
        String ammoProblem = AmmoRules.checkIssue(req.ammoRounds());
        if (ammoProblem != null) {
            throw new HandyFlowException("Rounds issued must be between 0 and " + AmmoRules.MAX_ROUNDS,
                    HttpStatus.BAD_REQUEST, ammoProblem);
        }

        Guard witness = guardRepository.findActiveById(tenantId, req.witnessedByGuardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard",
                        req.witnessedByGuardId().toString()));
        if (!witness.isSchedulable()) {
            throw new HandyFlowException(
                    "Witness " + witness.getFullName() + " is " + witness.getStatus()
                            + " — choose an active guard as witness",
                    HttpStatus.FORBIDDEN, "WITNESS_NOT_ACTIVE");
        }

        // ArmouryLog.record() itself enforces witness != guard, but we want a
        // clear message before constructing the log entity
        if (req.witnessedByGuardId().equals(req.guardId())) {
            throw new HandyFlowException(
                    "The witness must be a different guard from the one receiving the firearm",
                    HttpStatus.BAD_REQUEST, "INVALID_WITNESS");
        }

        ArmouryLog logEntry = ArmouryLog.record(
                tenantId, armouryId, req.guardId(), ArmouryLog.ArmouryAction.ISSUE,
                req.witnessedByGuardId(), req.sessionId(), req.shiftId(), req.conditionNotes());
        logEntry.recordAmmo(req.ammoCalibre(), req.ammoRounds(), null);
        logRepository.save(logEntry);

        firearm.markIssued(req.guardId());
        armouryRepository.save(firearm);

        writeAudit(tenantId, actorId, armouryId, "ISSUED",
                "{\"guardId\":\"" + req.guardId() + "\",\"witnessedByGuardId\":\"" + req.witnessedByGuardId()
                        + "\",\"ammoRounds\":" + (req.ammoRounds() == null ? "null" : req.ammoRounds()) + "}");

        log.info("[Security] Firearm issued serial={} guard={} witness={}",
                firearm.getFirearmSerial(), req.guardId(), req.witnessedByGuardId());

        return toResponse(firearm, tenantId);
    }

    /**
     * Returns a firearm to the armoury, with mandatory two-person witness.
     */
    @Transactional
    public ArmouryResponse returnFirearm(TenantId tenantId, UUID armouryId,
                                         ReturnFirearmRequest req, UUID actorId) {
        Armoury firearm = findActive(tenantId, armouryId);

        if (firearm.getStatus() != Armoury.ArmouryStatus.ISSUED) {
            throw new HandyFlowException(
                    "Firearm " + firearm.getFirearmSerial() + " is " + firearm.getStatus()
                            + " — nothing to return",
                    HttpStatus.CONFLICT, "FIREARM_NOT_ISSUED");
        }

        UUID holdingGuardId = firearm.getAssignedGuardId();

        Guard witness = guardRepository.findActiveById(tenantId, req.witnessedByGuardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard",
                        req.witnessedByGuardId().toString()));
        if (!witness.isSchedulable()) {
            throw new HandyFlowException(
                    "Witness " + witness.getFullName() + " is " + witness.getStatus()
                            + " — choose an active guard as witness",
                    HttpStatus.FORBIDDEN, "WITNESS_NOT_ACTIVE");
        }
        if (req.witnessedByGuardId().equals(holdingGuardId)) {
            throw new HandyFlowException(
                    "The witness must be a different guard from the one returning the firearm",
                    HttpStatus.BAD_REQUEST, "INVALID_WITNESS");
        }

        // The rounds that went out are on the most recent log entry of an ISSUED firearm (its ISSUE entry).
        ArmouryLog issueEntry = logRepository.findMostRecent(armouryId)
                .filter(l -> l.getAction() == ArmouryLog.ArmouryAction.ISSUE).orElse(null);
        Integer roundsOut = issueEntry == null ? null : issueEntry.getAmmoRounds();
        String ammoProblem = AmmoRules.checkReturn(roundsOut, req.ammoRoundsReturned(), req.ammoUnaccountedReason());
        if (ammoProblem != null) {
            throw new HandyFlowException(switch (ammoProblem) {
                case "AMMO_COUNT_REQUIRED"        -> "Enter how many rounds were handed back (" + roundsOut + " were issued)";
                case "AMMO_RETURN_EXCEEDS_ISSUED" -> "More rounds were returned than were issued (" + roundsOut + ")";
                case "AMMO_EXPLANATION_REQUIRED"  -> "Fewer rounds came back than went out: explain the " + AmmoRules.shortfall(roundsOut, req.ammoRoundsReturned()) + " unaccounted for";
                default                           -> "Rounds returned must be between 0 and " + AmmoRules.MAX_ROUNDS;
            }, HttpStatus.BAD_REQUEST, ammoProblem);
        }
        int shortfall = AmmoRules.shortfall(roundsOut, req.ammoRoundsReturned());

        ArmouryLog logEntry = ArmouryLog.record(
                tenantId, armouryId, holdingGuardId, ArmouryLog.ArmouryAction.RETURN,
                req.witnessedByGuardId(), null, null, req.conditionNotes());
        logEntry.recordAmmo(issueEntry == null ? null : issueEntry.getAmmoCalibre(), req.ammoRoundsReturned(),
                shortfall > 0 ? req.ammoUnaccountedReason() : null);
        logRepository.save(logEntry);
        if (shortfall > 0) {
            log.warn("[Security] Ammunition shortfall on return serial={} issued={} returned={}",
                    firearm.getFirearmSerial(), roundsOut, req.ammoRoundsReturned());
        }

        firearm.markReturned();
        armouryRepository.save(firearm);

        writeAudit(tenantId, actorId, armouryId, "RETURNED",
                "{\"guardId\":\"" + holdingGuardId + "\",\"witnessedByGuardId\":\"" + req.witnessedByGuardId()
                        + "\",\"ammoShortfall\":" + shortfall + "}");

        log.info("[Security] Firearm returned serial={} byGuard={} witness={}",
                firearm.getFirearmSerial(), holdingGuardId, req.witnessedByGuardId());

        return toResponse(firearm, tenantId);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ArmouryLog> getHistory(TenantId tenantId, UUID armouryId) {
        return logRepository.findByArmoury(tenantId, armouryId);
    }

    @Transactional(readOnly = true)
    public List<ArmouryResponse> getIssuedToGuard(TenantId tenantId, UUID guardId) {
        return armouryRepository.findIssuedToGuard(tenantId, guardId).stream()
                .map(a -> toResponse(a, tenantId))
                .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Competency comes from the guard's Skills tab (FIREARM_COMPETENCY rows, per category) and, in legacy mode,
     * the old guard-level date. See CompetencyChecks for the rules and handyflow.security.armoury.strict-competency.
     */
    private void checkCompetency(TenantId tenantId, Guard guard, Armoury firearm) {
        java.util.List<CompetencyChecks.Cert> certs = competencyRepository == null ? java.util.List.of()
                : competencyRepository.findActiveForGuard(tenantId, guard.getId()).stream()
                        .filter(c -> c.getType() == za.co.handyflow.platform.security.domain.model.GuardCompetency.Type.FIREARM_COMPETENCY)
                        .map(c -> new CompetencyChecks.Cert(c.getFirearmCategory(), c.getExpiryDate(), c.isVerified()))
                        .toList();
        String category = CompetencyChecks.categoryOf(firearm.getFirearmType());
        String problem = CompetencyChecks.check(category, certs, guard.hasFirearmCompetency(), strictCompetency,
                java.time.LocalDate.now());
        if (problem == null) return;
        String what = category == null ? "firearm" : category.toLowerCase();
        throw new HandyFlowException(problem.equals("NO_COMPETENCY_FOR_CATEGORY")
                ? "Guard " + guard.getFullName() + " has no valid " + what + " competency (they are competent on other categories only)"
                : "Guard " + guard.getFullName() + " has no valid firearm competency certificate on file: add it on the Skills tab",
                HttpStatus.FORBIDDEN, problem);
    }

    /** The shift, when given, must be this guard's and be current; handyflow.security.armoury.require-shift makes it mandatory. */
    private void checkShift(TenantId tenantId, IssueFirearmRequest req) {
        za.co.handyflow.platform.security.domain.model.Shift shift = null;
        if (req.shiftId() != null && shiftRepository != null) {
            shift = shiftRepository.findActiveById(tenantId, req.shiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift", req.shiftId().toString()));
        }
        String problem = IssueChecks.shift(requireShift, req.guardId(),
                shift == null ? null : shift.getGuardId(),
                shift == null ? null : shift.getStatus().name(),
                shift == null ? null : shift.getStartAt(),
                shift == null ? null : shift.getEndAt(),
                java.time.Instant.now());
        if (problem != null) {
            throw new HandyFlowException(switch (problem) {
                case "SHIFT_REQUIRED"   -> "Choose the shift this firearm is issued for";
                case "SHIFT_NOT_GUARDS" -> "That shift belongs to a different guard";
                case "SHIFT_NOT_ACTIVE" -> "That shift is no longer active";
                default                 -> "That shift is not on now (firearms are issued up to 2 hours before it starts)";
            }, HttpStatus.CONFLICT, problem);
        }
    }

    private Armoury findActive(TenantId tenantId, UUID id) {
        return armouryRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Armoury", id.toString()));
    }

    private ArmouryResponse toResponse(Armoury a, TenantId tenantId) {
        String guardName = a.getAssignedGuardId() != null
                ? guardRepository.findActiveById(tenantId, a.getAssignedGuardId())
                .map(Guard::getFullName).orElse("Unknown")
                : null;

        ArmouryLog out = a.getStatus() == Armoury.ArmouryStatus.ISSUED
                ? logRepository.findMostRecent(a.getId()).filter(l -> l.getAction() == ArmouryLog.ArmouryAction.ISSUE).orElse(null)
                : null;
        return new ArmouryResponse(
                a.getId(), a.getFirearmSerial(), a.getFirearmType(), a.getMakeModel(),
                a.getSapsLicenseNumber(), a.getLicenseIssuedAt(), a.getLicenseExpiry(),
                a.isLicenseExpired(), a.getAssignedGuardId(), guardName,
                a.getStatus().name(), a.getLastServiceAt(), a.getNextServiceDueAt(),
                a.getNotes(), a.getCreatedAt(),
                a.getSapsCaseNumber(), a.getLostReportedAt(), a.getSapsReportDueAt(),
                LostReportRules.overdue(java.time.Instant.now(), a.getSapsReportDueAt(), a.getSapsCaseNumber()),
                out == null ? null : out.getAmmoRounds(), out == null ? null : out.getAmmoCalibre());
    }

    // FIX: closes the confirmed AuditEvent coverage gap for Armoury.
    // Mirrors ShiftService.writeAudit()'s own established helper
    // pattern exactly, entityType "ARMOURY".
    private void writeAudit(TenantId tenantId, UUID actorId, UUID armouryId, String action, String metadataJson) {
        auditRepository.save(za.co.handyflow.platform.security.domain.model.AuditEvent.record(
                tenantId, actorId,
                za.co.handyflow.platform.security.domain.model.AuditEvent.ActorType.USER,
                "ARMOURY", armouryId, action,
                null, null, metadataJson));
    }
}
