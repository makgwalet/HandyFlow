// security/application/internal/CloseProtectionService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CloseProtectionService — principals, protection details, team assignments,
 * itinerary stops, plus V211 additions: evidence upload, arms<->CP linkage,
 * and clone-from-previous-detail.
 *
 * CONFIDENTIALITY BOUNDARY (Part 9.3) — unchanged from original, see
 * PrincipalResponse/PrincipalSummaryResponse split described below.
 *
 * NEW DEPENDENCIES (V211): CpEvidenceRepository, ArmouryService,
 * ArmouryLogRepository. Add these to the constructor field list alongside
 * the existing ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloseProtectionService {

    private final PrincipalRepository         principalRepository;
    private final ProtectionDetailRepository  detailRepository;
    private final DetailAssignmentRepository  assignmentRepository;
    private final ItineraryStopRepository     itineraryRepository;
    private final GuardRepository             guardRepository;
    private final AdvanceSurveyRepository     surveyRepository;
    private final ProtectionVehicleRepository vehicleRepository;
    private final AuditEventRepository        auditRepository;
    private final za.co.handyflow.platform.shared.FieldEncryptionService encryptionService;

    // ── V211 additions ─────────────────────────────────────────────────────────
    private final CpEvidenceRepository        evidenceRepository;
    private final ArmouryService              armouryService;
    private final ArmouryLogRepository        armouryLogRepository;

    // ── Principal CRUD (VIP_DETAIL_ACCESS required — full detail) ─────────────

    @Transactional
    public PrincipalResponse createPrincipal(TenantId tenantId, CreatePrincipalRequest req) {
        if (principalRepository.existsByCodename(tenantId, req.aliasCodename())) {
            throw new HandyFlowException(
                    "A principal with codename '" + req.aliasCodename() + "' already exists",
                    HttpStatus.CONFLICT, "DUPLICATE_CODENAME");
        }

        Principal.ThreatLevel threatLevel = parseThreatLevel(req.threatLevel());

        Principal principal = Principal.create(
                tenantId, req.fullName(), req.aliasCodename(), threatLevel,
                encryptionService.encrypt(req.medicalNotes()),
                encryptionService.encrypt(req.knownThreats()),
                req.emergencyContactsJson());
        principalRepository.save(principal);

        log.info("[Security] Principal created codename={} tenant={}",
                req.aliasCodename(), tenantId.getValue());

        return toFullResponse(principal);
    }

    @Transactional
    public PrincipalResponse getPrincipal(TenantId tenantId, UUID id, UUID actorId) {
        Principal principal = findPrincipal(tenantId, id);
        auditRepository.save(AuditEvent.recordView(
                tenantId, actorId, "PRINCIPAL", id,
                "{\"codename\":\"" + principal.getAliasCodename() + "\"}"));
        return toFullResponse(principal);
    }

    @Transactional
    public Page<PrincipalResponse> getAllPrincipals(TenantId tenantId, Pageable pageable,
                                                    UUID actorId) {
        Page<Principal> page = principalRepository.findAllActive(tenantId, pageable);
        page.forEach(p -> auditRepository.save(AuditEvent.recordView(
                tenantId, actorId, "PRINCIPAL", p.getId(),
                "{\"codename\":\"" + p.getAliasCodename() + "\",\"context\":\"list_view\"}")));
        return page.map(this::toFullResponse);
    }

    @Transactional
    public PrincipalResponse updatePrincipal(TenantId tenantId, UUID id,
                                             UpdatePrincipalRequest req) {
        Principal principal = findPrincipal(tenantId, id);

        if (!principal.getAliasCodename().equals(req.aliasCodename())
                && principalRepository.existsByCodename(tenantId, req.aliasCodename())) {
            throw new HandyFlowException(
                    "A principal with codename '" + req.aliasCodename() + "' already exists",
                    HttpStatus.CONFLICT, "DUPLICATE_CODENAME");
        }

        principal.update(req.fullName(), req.aliasCodename(), parseThreatLevel(req.threatLevel()),
                encryptionService.encrypt(req.medicalNotes()),
                encryptionService.encrypt(req.knownThreats()),
                req.emergencyContactsJson());
        principalRepository.save(principal);
        return toFullResponse(principal);
    }

    @Transactional
    public void deactivatePrincipal(TenantId tenantId, UUID id) {
        Principal principal = findPrincipal(tenantId, id);
        principal.deactivate();
        principalRepository.save(principal);
    }

    // ── Protection Detail CRUD ─────────────────────────────────────────────────

    @Transactional
    public ProtectionDetailResponse createDetail(TenantId tenantId,
                                                 CreateProtectionDetailRequest req) {
        Principal principal = findPrincipal(tenantId, req.principalId());

        ProtectionDetail.DetailType type;
        try {
            type = ProtectionDetail.DetailType.valueOf(req.detailType());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid detailType: " + req.detailType(),
                    HttpStatus.BAD_REQUEST, "INVALID_DETAIL_TYPE");
        }

        ProtectionDetail detail = ProtectionDetail.create(
                tenantId, req.principalId(), type, req.startAt(), req.endAt(),
                req.billingRate(), req.clientReference(), req.notes());
        detailRepository.save(detail);

        log.info("[Security] Protection detail created id={} principal={} type={}",
                detail.getId(), principal.getAliasCodename(), type);
        return toDetailResponse(detail, principal);
    }

    @Transactional(readOnly = true)
    public ProtectionDetailResponse getDetail(TenantId tenantId, UUID id) {
        ProtectionDetail detail = findDetail(tenantId, id);
        Principal principal     = findPrincipal(tenantId, detail.getPrincipalId());
        return toDetailResponse(detail, principal);
    }

    @Transactional(readOnly = true)
    public List<ProtectionDetailResponse> getDetailsForPrincipal(TenantId tenantId,
                                                                 UUID principalId) {
        Principal principal = findPrincipal(tenantId, principalId);
        return detailRepository.findByPrincipal(tenantId, principalId).stream()
                .map(d -> toDetailResponse(d, principal))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ProtectionDetailResponse> getActiveOrPlannedDetails(TenantId tenantId,
                                                                    Pageable pageable) {
        return detailRepository.findActiveOrPlanned(tenantId, pageable)
                .map(d -> toDetailResponse(d, findPrincipal(tenantId, d.getPrincipalId())));
    }

    @Transactional
    public ProtectionDetailResponse activateDetail(TenantId tenantId, UUID id) {
        ProtectionDetail detail = findDetail(tenantId, id);
        detail.activate();
        detailRepository.save(detail);
        return toDetailResponse(detail, findPrincipal(tenantId, detail.getPrincipalId()));
    }

    @Transactional
    public ProtectionDetailResponse completeDetail(TenantId tenantId, UUID id) {
        ProtectionDetail detail = findDetail(tenantId, id);
        detail.complete();
        detailRepository.save(detail);
        return toDetailResponse(detail, findPrincipal(tenantId, detail.getPrincipalId()));
    }

    @Transactional
    public ProtectionDetailResponse cancelDetail(TenantId tenantId, UUID id,
                                                 CancelDetailRequest req) {
        ProtectionDetail detail = findDetail(tenantId, id);
        detail.cancel(req.reason());
        detailRepository.save(detail);
        return toDetailResponse(detail, findPrincipal(tenantId, detail.getPrincipalId()));
    }

    // ── Clone-from-previous (V211) ─────────────────────────────────────────────

    /**
     * Spins up a new ProtectionDetail from a previous one for the same
     * principal — the "VC visits campus every semester" case. Copies
     * principalId/detailType/billingRate/notes from the source, then
     * re-validates and re-creates the team roster and itinerary rather than
     * blindly copying rows.
     *
     * WHY re-validate team assignments through assignToDetail() instead of
     * copying DetailAssignment rows directly?
     * A guard who covered the last engagement might have let their CP
     * vetting tier lapse, gone SUSPENDED, or otherwise become ineligible
     * since then. assignToDetail() already enforces the hard vetting-tier
     * gate (same pattern as ArmouryService.issue()'s competency check) —
     * routing through it means cloning can never silently create an
     * assignment that would have been rejected if done manually. Guards that
     * fail are skipped and reported back, not silently dropped.
     *
     * WHY recompute itinerary timing by offset rather than copying timestamps?
     * The source detail's scheduledArrival/scheduledDeparture are for a
     * specific past (or future) date — copying them verbatim onto a new
     * detail with a different startAt would put stops on the wrong day.
     * Preserving the *relative* offset from the source detail's start
     * reproduces "same day-of, same running order" without requiring the
     * caller to re-enter every stop's timing by hand.
     */
    @Transactional
    public CloneDetailResult cloneDetail(TenantId tenantId, UUID sourceDetailId,
                                         UUID actorId, CloneDetailRequest req) {
        ProtectionDetail source = findDetail(tenantId, sourceDetailId);
        Principal principal     = findPrincipal(tenantId, source.getPrincipalId());

        ProtectionDetail newDetail = ProtectionDetail.create(
                tenantId, source.getPrincipalId(), source.getDetailType(),
                req.startAt(), req.endAt(), source.getBillingRate(),
                req.clientReference() != null ? req.clientReference() : source.getClientReference(),
                source.getNotes());
        detailRepository.save(newDetail);

        // ── Clone team roster (re-validated, not copied) ────────────────────────
        List<String> skipped = new ArrayList<>();
        int teamCloned = 0;
        for (DetailAssignment a : assignmentRepository.findActiveByDetail(sourceDetailId)) {
            try {
                AssignToDetailRequest assignReq = new AssignToDetailRequest(
                        a.getGuardId(), a.getRole().name(), null);
                assignToDetail(tenantId, newDetail.getId(), assignReq);
                teamCloned++;
            } catch (HandyFlowException e) {
                String guardName = guardRepository.findActiveById(tenantId, a.getGuardId())
                        .map(Guard::getFullName).orElse(a.getGuardId().toString());
                skipped.add(guardName + " (" + a.getRole() + "): " + e.getMessage());
                log.warn("[Security] Clone-detail skipped team member sourceDetail={} newDetail={} guard={}: {}",
                        sourceDetailId, newDetail.getId(), a.getGuardId(), e.getMessage());
            }
        }

        // ── Clone itinerary (offset-preserved timing) ────────────────────────────
        int stopsCloned = 0;
        for (ItineraryStop stop : itineraryRepository.findByDetail(sourceDetailId)) {
            Instant newArrival   = shiftByOffset(source.getStartAt(), req.startAt(), stop.getScheduledArrival());
            Instant newDeparture = shiftByOffset(source.getStartAt(), req.startAt(), stop.getScheduledDeparture());

            AddItineraryStopRequest stopReq = new AddItineraryStopRequest(
                    stop.getLocationName(), stop.getAddress(), stop.getLatitude(), stop.getLongitude(),
                    newArrival, newDeparture, stop.isAdvanceSurveyRequired(), stop.getNotes());
            addStop(tenantId, newDetail.getId(), stopReq);
            stopsCloned++;
        }

        auditRepository.save(AuditEvent.record(
                tenantId, actorId, AuditEvent.ActorType.USER,
                "PROTECTION_DETAIL", newDetail.getId(), "DETAIL_CLONED",
                null, null,
                "{\"sourceDetailId\":\"" + sourceDetailId + "\",\"teamCloned\":" + teamCloned
                        + ",\"stopsCloned\":" + stopsCloned + "}"));

        log.info("[Security] Detail cloned sourceDetail={} newDetail={} principal={} teamCloned={} skipped={} stopsCloned={}",
                sourceDetailId, newDetail.getId(), principal.getAliasCodename(),
                teamCloned, skipped.size(), stopsCloned);

        return new CloneDetailResult(toDetailResponse(newDetail, principal), teamCloned, skipped, stopsCloned);
    }

    /** Shifts a timestamp by the same delta between an old and new reference point. Null-safe. */
    private Instant shiftByOffset(Instant oldReference, Instant newReference, Instant value) {
        if (value == null || oldReference == null || newReference == null) return null;
        Duration offset = Duration.between(oldReference, value);
        return newReference.plus(offset);
    }

    // ── Team Assignments ───────────────────────────────────────────────────────

    @Transactional
    public DetailAssignmentResponse assignToDetail(TenantId tenantId, UUID detailId,
                                                   AssignToDetailRequest req) {
        ProtectionDetail detail = findDetail(tenantId, detailId);

        Guard guard = guardRepository.findActiveById(tenantId, req.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard", req.guardId().toString()));

        if (!guard.isSchedulable()) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " is " + guard.getStatus()
                            + " and cannot be assigned to a protection detail",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_SCHEDULABLE");
        }

        Principal principal = findPrincipal(tenantId,
                findDetail(tenantId, detailId).getPrincipalId());
        if (!guard.meetsVettingTierFor(principal.getThreatLevel().name())) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " does not have sufficient CP vetting "
                            + "clearance for a " + principal.getThreatLevel() + " threat-level principal. "
                            + "Current tier: " + (guard.getCpVettingTier() != null
                            ? guard.getCpVettingTier() : "NONE"),
                    HttpStatus.FORBIDDEN, "INSUFFICIENT_CP_VETTING");
        }

        if (assignmentRepository.hasOpenAssignment(detailId, req.guardId())) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " already has an active role on this detail",
                    HttpStatus.CONFLICT, "ALREADY_ASSIGNED");
        }

        DetailAssignment.DetailRole role;
        try {
            role = DetailAssignment.DetailRole.valueOf(req.role());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid role: " + req.role(),
                    HttpStatus.BAD_REQUEST, "INVALID_ROLE");
        }

        Instant start = req.assignmentStart() != null ? req.assignmentStart() : Instant.now();

        DetailAssignment assignment = DetailAssignment.create(
                tenantId, detailId, req.guardId(), role, start);
        assignmentRepository.save(assignment);

        log.info("[Security] Guard assigned to detail detailId={} guard={} role={}",
                detailId, guard.getFullName(), role);

        return toAssignmentResponse(assignment, guard);
    }

    @Transactional
    public void endAssignment(TenantId tenantId, UUID assignmentId) {
        DetailAssignment assignment = assignmentRepository.findByTenantAndId(tenantId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DetailAssignment",
                        assignmentId.toString()));
        assignment.end(Instant.now());
        assignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public List<DetailAssignmentResponse> getTeamRoster(TenantId tenantId, UUID detailId) {
        return assignmentRepository.findActiveByDetail(detailId).stream()
                .map(a -> {
                    Guard guard = guardRepository.findActiveById(tenantId, a.getGuardId())
                            .orElse(null);
                    return toAssignmentResponse(a, guard);
                })
                .toList();
    }

    // ── Arms <-> CP linkage (V211) ────────────────────────────────────────────

    /**
     * Issues a firearm as part of a CP detail's team roster — a thin wrapper
     * around ArmouryService.issue() that adds CP-specific context, not a
     * reimplementation of the witnessed-issue workflow. All of ArmouryService's
     * hard blocks (license expiry, firearm availability, receiving guard's
     * competency, witness validity) apply unchanged.
     *
     * Validates that the target assignment actually belongs to this detail
     * and to the guard named in the armoury request — prevents issuing a
     * firearm "for" a detail to a guard who isn't actually on its roster.
     */
    @Transactional
    public ArmouryResponse issueFirearmForDetail(TenantId tenantId, UUID detailId,
                                                 UUID assignmentId, UUID armouryId,
                                                 IssueFirearmRequest req) {
        findDetail(tenantId, detailId); // validates detail exists + belongs to tenant

        DetailAssignment assignment = assignmentRepository.findByTenantAndId(tenantId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DetailAssignment", assignmentId.toString()));

        if (!assignment.getDetailId().equals(detailId)) {
            throw new HandyFlowException(
                    "That assignment does not belong to this detail",
                    HttpStatus.BAD_REQUEST, "ASSIGNMENT_DETAIL_MISMATCH");
        }
        if (!assignment.isActive()) {
            throw new HandyFlowException(
                    "That guard's role on this detail has already ended",
                    HttpStatus.CONFLICT, "ASSIGNMENT_ENDED");
        }
        if (!assignment.getGuardId().equals(req.guardId())) {
            throw new HandyFlowException(
                    "The receiving guard on the armoury request must match the assignment's guard",
                    HttpStatus.BAD_REQUEST, "GUARD_MISMATCH");
        }

        ArmouryResponse response = armouryService.issue(tenantId, armouryId, req);

        // Link the log entry ArmouryService just created to this detail.
        // ArmouryService itself has no knowledge of CP details -- linking
        // happens here, one level up, exactly the same separation
        // ArmouryController's javadoc already draws for CP-specific workflows.
        armouryLogRepository.findMostRecent(armouryId).ifPresent(logEntry -> {
            logEntry.linkProtectionDetail(detailId);
            armouryLogRepository.save(logEntry);
        });

        log.info("[Security] Firearm issued for CP detail detailId={} assignment={} guard={}",
                detailId, assignmentId, req.guardId());

        return response;
    }

    /** All issue/return events linked to this detail — "which firearms are/were out on this engagement." */
    @Transactional(readOnly = true)
    public List<ArmouryLog> getArmouryForDetail(TenantId tenantId, UUID detailId) {
        findDetail(tenantId, detailId);
        return armouryLogRepository.findByProtectionDetail(tenantId, detailId);
    }

    // ── Itinerary Stops ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryStopResponse addStop(TenantId tenantId, UUID detailId,
                                         AddItineraryStopRequest req) {
        findDetail(tenantId, detailId);

        int nextSequence = itineraryRepository.findMaxSequence(detailId) + 1;

        ItineraryStop stop = ItineraryStop.create(
                tenantId, detailId, nextSequence, req.locationName(), req.address(),
                req.latitude(), req.longitude(), req.scheduledArrival(), req.scheduledDeparture(),
                req.advanceSurveyRequired(), req.notes());
        itineraryRepository.save(stop);

        return toStopResponse(stop);
    }

    @Transactional(readOnly = true)
    public List<ItineraryStopResponse> getItinerary(TenantId tenantId, UUID detailId) {
        findDetail(tenantId, detailId);
        return itineraryRepository.findByDetail(detailId).stream()
                .map(this::toStopResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ItineraryStopResponse getCurrentStop(TenantId tenantId, UUID detailId) {
        findDetail(tenantId, detailId);
        return itineraryRepository.findCurrentStop(detailId)
                .map(this::toStopResponse)
                .orElse(null);
    }

    @Transactional
    public ItineraryStopResponse recordStopArrival(TenantId tenantId, UUID stopId) {
        ItineraryStop stop = findStop(tenantId, stopId);
        stop.recordArrival();
        itineraryRepository.save(stop);
        return toStopResponse(stop);
    }

    @Transactional
    public ItineraryStopResponse recordStopDeparture(TenantId tenantId, UUID stopId) {
        ItineraryStop stop = findStop(tenantId, stopId);
        stop.recordDeparture();
        itineraryRepository.save(stop);
        return toStopResponse(stop);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AuditEvent> getPrincipalAudit(
            TenantId tenantId, UUID principalId,
            org.springframework.data.domain.Pageable pageable) {
        findPrincipal(tenantId, principalId);
        return auditRepository.findByEntity(tenantId, "PRINCIPAL", principalId, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AuditEvent> getPrincipalViewHistory(
            TenantId tenantId, UUID principalId,
            org.springframework.data.domain.Pageable pageable) {
        findPrincipal(tenantId, principalId);
        return auditRepository.findViewHistory(tenantId, "PRINCIPAL", principalId, pageable);
    }

    // ── Part 9.4: Guard-facing CP profile (Shield app) ────────────────────────

    @Transactional(readOnly = true)
    public GuardCpProfileResponse getGuardCpProfile(TenantId tenantId, UUID guardId) {
        List<DetailAssignment> active = assignmentRepository.findActiveByGuard(tenantId, guardId);

        if (active.isEmpty()) {
            return new GuardCpProfileResponse(false, null, null, null, null, null, null, List.of());
        }

        DetailAssignment assignment = active.stream()
                .max(java.util.Comparator.comparing(DetailAssignment::getAssignmentStart))
                .get();

        ProtectionDetail detail = detailRepository.findById(assignment.getDetailId())
                .orElse(null);
        if (detail == null) {
            return new GuardCpProfileResponse(false, null, null, null, null, null, null, List.of());
        }

        Principal principal = principalRepository.findById(detail.getPrincipalId()).orElse(null);
        String codename     = principal != null ? principal.getAliasCodename() : "UNKNOWN";
        String threatLevel  = principal != null ? principal.getThreatLevel().name() : "LOW";

        List<ItineraryStopResponse> upcomingStops = itineraryRepository
                .findByDetail(detail.getId()).stream()
                .filter(s -> s.getActualDeparture() == null)
                .limit(3)
                .map(this::toStopResponse)
                .toList();

        return new GuardCpProfileResponse(
                true, detail.getId(), codename, threatLevel,
                assignment.getRole().name(), detail.getStartAt(), detail.getEndAt(),
                upcomingStops);
    }

    // ── Advance Surveys ────────────────────────────────────────────────────────

    @Transactional
    public AdvanceSurveyResponse conductSurvey(TenantId tenantId, UUID stopId,
                                               UUID surveyingGuardId,
                                               ConductSurveyRequest req) {
        findStop(tenantId, stopId);

        Guard guard = guardRepository.findActiveById(tenantId, surveyingGuardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard",
                        surveyingGuardId.toString()));

        AdvanceSurvey survey = AdvanceSurvey.conduct(
                tenantId, stopId, surveyingGuardId, req.entryExitRoutesNotes(),
                req.hazardsNoted(), req.photoUrlsJson(), req.allClear());
        surveyRepository.save(survey);

        log.info("[Security] Advance survey conducted stopId={} guard={} allClear={}",
                stopId, guard.getFullName(), req.allClear());

        return toSurveyResponse(survey, guard);
    }

    @Transactional(readOnly = true)
    public List<AdvanceSurveyResponse> getSurveysForStop(TenantId tenantId, UUID stopId) {
        findStop(tenantId, stopId);
        return surveyRepository.findByStop(stopId).stream()
                .map(s -> toSurveyResponse(s,
                        guardRepository.findActiveById(tenantId, s.getSurveyedByGuardId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isStopCleared(UUID stopId) {
        return surveyRepository.hasAllClearSurvey(stopId);
    }

    // ── Protection Vehicles ────────────────────────────────────────────────────

    @Transactional
    public VehicleResponse registerVehicle(TenantId tenantId, RegisterVehicleRequest req) {
        String normalizedReg = req.registration().strip().toUpperCase();
        if (vehicleRepository.existsByRegistration(tenantId, normalizedReg)) {
            throw new HandyFlowException(
                    "A vehicle with registration " + normalizedReg + " is already registered",
                    HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION");
        }

        ProtectionVehicle.VehicleType type;
        try {
            type = ProtectionVehicle.VehicleType.valueOf(req.vehicleType());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid vehicleType: " + req.vehicleType(),
                    HttpStatus.BAD_REQUEST, "INVALID_VEHICLE_TYPE");
        }

        ProtectionVehicle vehicle = ProtectionVehicle.register(
                tenantId, type, normalizedReg, req.makeModel(), req.armored(), req.notes());
        vehicleRepository.save(vehicle);

        log.info("[Security] Protection vehicle registered reg={} type={}", normalizedReg, type);
        return toVehicleResponse(vehicle, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<VehicleResponse> getAllVehicles(TenantId tenantId, Pageable pageable) {
        return vehicleRepository.findAllActive(tenantId, pageable)
                .map(v -> toVehicleResponse(v, tenantId));
    }

    @Transactional
    public VehicleResponse assignDriver(TenantId tenantId, UUID vehicleId,
                                        AssignDriverRequest req) {
        ProtectionVehicle vehicle = findVehicle(tenantId, vehicleId);

        Guard guard = guardRepository.findActiveById(tenantId, req.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard", req.guardId().toString()));
        if (!guard.isSchedulable()) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " is " + guard.getStatus()
                            + " and cannot be assigned as a driver",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_SCHEDULABLE");
        }

        vehicle.assignDriver(req.guardId());
        vehicleRepository.save(vehicle);
        return toVehicleResponse(vehicle, tenantId);
    }

    @Transactional
    public VehicleResponse releaseDriver(TenantId tenantId, UUID vehicleId) {
        ProtectionVehicle vehicle = findVehicle(tenantId, vehicleId);
        vehicle.releaseDriver();
        vehicleRepository.save(vehicle);
        return toVehicleResponse(vehicle, tenantId);
    }

    @Transactional
    public VehicleResponse sendForService(TenantId tenantId, UUID vehicleId,
                                          ServiceVehicleRequest req) {
        ProtectionVehicle vehicle = findVehicle(tenantId, vehicleId);
        vehicle.sendForService(req.notes());
        vehicleRepository.save(vehicle);
        return toVehicleResponse(vehicle, tenantId);
    }

    @Transactional
    public VehicleResponse returnFromService(TenantId tenantId, UUID vehicleId) {
        ProtectionVehicle vehicle = findVehicle(tenantId, vehicleId);
        vehicle.returnFromService();
        vehicleRepository.save(vehicle);
        return toVehicleResponse(vehicle, tenantId);
    }

    @Transactional
    public VehicleResponse decommissionVehicle(TenantId tenantId, UUID vehicleId) {
        ProtectionVehicle vehicle = findVehicle(tenantId, vehicleId);
        vehicle.decommission();
        vehicleRepository.save(vehicle);
        return toVehicleResponse(vehicle, tenantId);
    }

    // ── Evidence (V211) ────────────────────────────────────────────────────────

    /**
     * Uploads an evidence document attached to either a Principal or a
     * ProtectionDetail. Dev-mode base64 handling mirrors
     * GuardService.updatePhoto() exactly: a data URI is accepted but stored
     * as a "PENDING_UPLOAD" placeholder with a warning logged, rather than
     * growing the DB row or silently failing. Production callers should send
     * a real fileUrl from a presigned S3 upload instead.
     */
    @Transactional
    public EvidenceResponse uploadEvidence(TenantId tenantId, CpEvidence.EntityType entityType,
                                           UUID entityId, UploadEvidenceRequest req, UUID uploadedBy) {
        validateEvidenceParent(tenantId, entityType, entityId);

        CpEvidence.Category category;
        try {
            category = CpEvidence.Category.valueOf(req.category());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid category: " + req.category(),
                    HttpStatus.BAD_REQUEST, "INVALID_EVIDENCE_CATEGORY");
        }

        String fileUrl = req.fileUrl();
        if ((fileUrl == null || fileUrl.isBlank())
                && req.fileBase64() != null && req.fileBase64().startsWith("data:")) {
            log.warn("[Security] Base64 evidence file received for {} {} — stored as PENDING_UPLOAD. "
                            + "Wire up S3 presigned URL before production deployment.",
                    entityType, entityId);
            fileUrl = "PENDING_UPLOAD";
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new HandyFlowException("Either fileUrl or fileBase64 is required",
                    HttpStatus.BAD_REQUEST, "MISSING_FILE");
        }

        CpEvidence evidence = CpEvidence.upload(
                tenantId, entityType, entityId, category, fileUrl,
                req.fileName(), req.notes(), uploadedBy);
        evidenceRepository.save(evidence);

        auditRepository.save(AuditEvent.record(
                tenantId, uploadedBy, AuditEvent.ActorType.USER,
                entityType.name(), entityId, "EVIDENCE_UPLOADED",
                null, null,
                "{\"category\":\"" + category + "\",\"evidenceId\":\"" + evidence.getId() + "\"}"));

        log.info("[Security] Evidence uploaded {} entityId={} category={} by={}",
                entityType, entityId, category, uploadedBy);

        return toEvidenceResponse(evidence);
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidenceFor(TenantId tenantId, CpEvidence.EntityType entityType,
                                                 UUID entityId) {
        validateEvidenceParent(tenantId, entityType, entityId);
        return evidenceRepository.findActiveForEntity(tenantId, entityType, entityId).stream()
                .map(this::toEvidenceResponse)
                .toList();
    }

    @Transactional
    public void deleteEvidence(TenantId tenantId, UUID evidenceId, UUID actorId,
                               DeleteEvidenceRequest req) {
        CpEvidence evidence = evidenceRepository.findActiveById(tenantId, evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("CpEvidence", evidenceId.toString()));

        evidence.softDelete(actorId, req.reason());
        evidenceRepository.save(evidence);

        auditRepository.save(AuditEvent.record(
                tenantId, actorId, AuditEvent.ActorType.USER,
                evidence.getEntityType().name(), evidence.getEntityId(), "EVIDENCE_DELETED",
                null, null,
                "{\"evidenceId\":\"" + evidenceId + "\",\"reason\":\""
                        + req.reason().replace("\"", "\\\"") + "\"}"));

        log.warn("[Security] Evidence deleted id={} by={} reason='{}'", evidenceId, actorId, req.reason());
    }

    private void validateEvidenceParent(TenantId tenantId, CpEvidence.EntityType entityType, UUID entityId) {
        switch (entityType) {
            case PRINCIPAL -> findPrincipal(tenantId, entityId);
            case PROTECTION_DETAIL -> findDetail(tenantId, entityId);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Principal findPrincipal(TenantId tenantId, UUID id) {
        return principalRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Principal", id.toString()));
    }

    private ProtectionDetail findDetail(TenantId tenantId, UUID id) {
        return detailRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ProtectionDetail", id.toString()));
    }

    private ItineraryStop findStop(TenantId tenantId, UUID id) {
        return itineraryRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ItineraryStop", id.toString()));
    }

    private ProtectionVehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ProtectionVehicle", id.toString()));
    }

    private Principal.ThreatLevel parseThreatLevel(String raw) {
        if (raw == null) return Principal.ThreatLevel.LOW;
        try {
            return Principal.ThreatLevel.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid threatLevel: " + raw,
                    HttpStatus.BAD_REQUEST, "INVALID_THREAT_LEVEL");
        }
    }

    private PrincipalResponse toFullResponse(Principal p) {
        return new PrincipalResponse(
                p.getId(), p.getFullName(), p.getAliasCodename(), p.getThreatLevel().name(),
                encryptionService.decrypt(p.getMedicalNotes()),
                encryptionService.decrypt(p.getKnownThreats()),
                p.getEmergencyContacts(),
                p.getPhotoUrl(), p.isActive(), p.getCreatedAt(),
                p.getVettingStatus());
    }

    private ProtectionDetailResponse toDetailResponse(ProtectionDetail d, Principal principal) {
        int teamSize = assignmentRepository.findActiveByDetail(d.getId()).size();
        return new ProtectionDetailResponse(
                d.getId(), d.getPrincipalId(), principal.getAliasCodename(),
                d.getDetailType().name(), d.getStartAt(), d.getEndAt(), d.getStatus().name(),
                d.getBillingRate(), d.getClientReference(), d.getNotes(), teamSize,
                d.getCreatedAt());
    }

    private DetailAssignmentResponse toAssignmentResponse(DetailAssignment a, Guard guard) {
        return new DetailAssignmentResponse(
                a.getId(), a.getDetailId(), a.getGuardId(),
                guard != null ? guard.getFullName() : "Unknown",
                a.getRole().name(), a.getAssignmentStart(), a.getAssignmentEnd(),
                a.isActive());
    }

    private ItineraryStopResponse toStopResponse(ItineraryStop s) {
        String status = s.isCompleted() ? "COMPLETED"
                : s.isInProgress() ? "IN_PROGRESS" : "PENDING";
        return new ItineraryStopResponse(
                s.getId(), s.getDetailId(), s.getSequence(), s.getLocationName(),
                s.getAddress(), s.getLatitude(), s.getLongitude(),
                s.getScheduledArrival(), s.getScheduledDeparture(),
                s.getActualArrival(), s.getActualDeparture(),
                s.isAdvanceSurveyRequired(), s.getNotes(), status);
    }

    private AdvanceSurveyResponse toSurveyResponse(AdvanceSurvey s, Guard guard) {
        return new AdvanceSurveyResponse(
                s.getId(), s.getItineraryStopId(), s.getSurveyedByGuardId(),
                guard != null ? guard.getFullName() : "Unknown", s.getSurveyedAt(),
                s.getEntryExitRoutesNotes(), s.getHazardsNoted(), s.getPhotoUrls(),
                s.isAllClear());
    }

    private VehicleResponse toVehicleResponse(ProtectionVehicle v, TenantId tenantId) {
        String driverName = v.getAssignedDriverGuardId() != null
                ? guardRepository.findActiveById(tenantId, v.getAssignedDriverGuardId())
                .map(Guard::getFullName).orElse("Unknown")
                : null;
        return new VehicleResponse(
                v.getId(), v.getVehicleType().name(), v.getRegistration(), v.getMakeModel(),
                v.isArmored(), v.getAssignedDriverGuardId(), driverName,
                v.getStatus().name(), v.getNotes(), v.getCreatedAt());
    }

    private EvidenceResponse toEvidenceResponse(CpEvidence e) {
        return new EvidenceResponse(
                e.getId(), e.getEntityType().name(), e.getEntityId(), e.getCategory().name(),
                e.getFileUrl(), e.getFileName(), e.getNotes(), e.getUploadedBy(), e.getCreatedAt());
    }
}