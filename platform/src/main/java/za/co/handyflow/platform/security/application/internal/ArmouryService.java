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
                                      ReportLostFirearmRequest req) {
        Armoury firearm = findActive(tenantId, id);
        firearm.reportLost(req.notes());
        armouryRepository.save(firearm);

        log.warn("[Security] Firearm reported LOST serial={} tenant={}",
                firearm.getFirearmSerial(), tenantId.getValue());
        return toResponse(firearm, tenantId);
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
    public ArmouryResponse issue(TenantId tenantId, UUID armouryId, IssueFirearmRequest req) {
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
        if (!receivingGuard.hasFirearmCompetency()) {
            throw new HandyFlowException(
                    "Guard " + receivingGuard.getFullName()
                            + " has no valid firearm competency certificate on file — cannot issue",
                    HttpStatus.FORBIDDEN, "NO_FIREARM_COMPETENCY");
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
        logRepository.save(logEntry);

        firearm.markIssued(req.guardId());
        armouryRepository.save(firearm);

        log.info("[Security] Firearm issued serial={} guard={} witness={}",
                firearm.getFirearmSerial(), req.guardId(), req.witnessedByGuardId());

        return toResponse(firearm, tenantId);
    }

    /**
     * Returns a firearm to the armoury, with mandatory two-person witness.
     */
    @Transactional
    public ArmouryResponse returnFirearm(TenantId tenantId, UUID armouryId,
                                         ReturnFirearmRequest req) {
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

        ArmouryLog logEntry = ArmouryLog.record(
                tenantId, armouryId, holdingGuardId, ArmouryLog.ArmouryAction.RETURN,
                req.witnessedByGuardId(), null, null, req.conditionNotes());
        logRepository.save(logEntry);

        firearm.markReturned();
        armouryRepository.save(firearm);

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

    private Armoury findActive(TenantId tenantId, UUID id) {
        return armouryRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Armoury", id.toString()));
    }

    private ArmouryResponse toResponse(Armoury a, TenantId tenantId) {
        String guardName = a.getAssignedGuardId() != null
                ? guardRepository.findActiveById(tenantId, a.getAssignedGuardId())
                .map(Guard::getFullName).orElse("Unknown")
                : null;

        return new ArmouryResponse(
                a.getId(), a.getFirearmSerial(), a.getFirearmType(), a.getMakeModel(),
                a.getSapsLicenseNumber(), a.getLicenseIssuedAt(), a.getLicenseExpiry(),
                a.isLicenseExpired(), a.getAssignedGuardId(), guardName,
                a.getStatus().name(), a.getLastServiceAt(), a.getNextServiceDueAt(),
                a.getNotes(), a.getCreatedAt());
    }
}
