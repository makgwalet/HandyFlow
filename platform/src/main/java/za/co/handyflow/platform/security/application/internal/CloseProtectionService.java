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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CloseProtectionService — principals, protection details, team assignments,
 * and itinerary stops. Core of the VIP/Close Protection module (Part 9).
 *
 * CONFIDENTIALITY BOUNDARY (Part 9.3):
 * This service exposes two response shapes for Principal data:
 *   - PrincipalResponse: full detail (real name, medical notes, threats) —
 *     only returned by methods explicitly requiring the caller to already
 *     hold VIP_DETAIL_ACCESS (enforced at the controller via @PreAuthorize,
 *     not re-checked here — this service trusts the controller gate).
 *   - PrincipalSummaryResponse: codename + threat level only — used when
 *     building ProtectionDetailResponse for general consumption (e.g. a
 *     supervisor's overview screen listing all engagements without needing
 *     to see who's actually being protected).
 *
 * WHY does the service still need to know which response shape to build,
 * rather than just always returning everything and letting the controller
 * filter?
 * Filtering after the fact risks the full data briefly existing in a
 * response object that could be logged, cached, or serialized somewhere
 * before filtering happens. Building the restricted shape from the start
 * is a stronger guarantee than trusting every caller to filter correctly.
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

    // ── Principal CRUD (VIP_DETAIL_ACCESS required — full detail) ─────────────

    @Transactional
    public PrincipalResponse createPrincipal(TenantId tenantId, CreatePrincipalRequest req) {
        if (principalRepository.existsByCodename(tenantId, req.aliasCodename())) {
            throw new HandyFlowException(
                    "A principal with codename '" + req.aliasCodename() + "' already exists",
                    HttpStatus.CONFLICT, "DUPLICATE_CODENAME");
        }

        Principal.ThreatLevel threatLevel = parseThreatLevel(req.threatLevel());

        // Encrypt at the service boundary — Principal's fields hold ciphertext only.
        Principal principal = Principal.create(
                tenantId, req.fullName(), req.aliasCodename(), threatLevel,
                encryptionService.encrypt(req.medicalNotes()),
                encryptionService.encrypt(req.knownThreats()),
                req.emergencyContactsJson());
        principalRepository.save(principal);

        log.info("[Security] Principal created codename={} tenant={}",
                req.aliasCodename(), tenantId.getValue());

        // No "VIEWED" audit on create — the creating supervisor obviously
        // already knows the data they just entered. Audit is for READS of
        // existing records, not the act of authoring them.
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

        // Part 9.5 vetting gate — the guard's CP clearance tier must meet
        // the minimum for this principal's threat level (hard block, same
        // as the firearm competency gate in ArmouryService).
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

    // ── Itinerary Stops ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryStopResponse addStop(TenantId tenantId, UUID detailId,
                                         AddItineraryStopRequest req) {
        findDetail(tenantId, detailId);  // validates detail exists + belongs to tenant

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
        findPrincipal(tenantId, principalId); // validate tenant scoping
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

    /**
     * Lightweight CP status for the Shield app's home screen.
     * Uses codename only — never real name. Returns an empty/inactive
     * profile if the guard has no current CP assignment.
     *
     * Not gated behind VIP_DETAIL_ACCESS — the guard themselves needs this
     * on startup to know if they're on a CP detail today. Codename-only
     * means no Part 9.3 confidentiality leak even without the authority gate.
     */
    @Transactional(readOnly = true)
    public GuardCpProfileResponse getGuardCpProfile(TenantId tenantId, UUID guardId) {
        List<DetailAssignment> active = assignmentRepository.findActiveByGuard(tenantId, guardId);

        if (active.isEmpty()) {
            return new GuardCpProfileResponse(false, null, null, null, null, null, null, List.of());
        }

        // Take the first active assignment — a guard shouldn't have more than
        // one open-ended role (enforced by the DB constraint), but if somehow
        // they do, take the most recently started one.
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

        // Only the next 3 upcoming stops for the initial response
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

    /**
     * Conducts an advance survey at an itinerary stop — the CP equivalent
     * of a patrol round (Phase 2): a guard recons the location and reports
     * whether it's clear before the principal arrives.
     *
     * Multiple surveys per stop are allowed (one per surveying guard) — a
     * high-threat-level detail may want independent confirmation from two
     * guards. The unique constraint is on (stop, guard), not the stop alone.
     */
    @Transactional
    public AdvanceSurveyResponse conductSurvey(TenantId tenantId, UUID stopId,
                                               UUID surveyingGuardId,
                                               ConductSurveyRequest req) {
        findStop(tenantId, stopId);  // validates stop exists + belongs to tenant

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

    /** Whether at least one ALL_CLEAR survey exists for a stop — the gate before the principal arrives. */
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
                p.getPhotoUrl(), p.isActive(), p.getCreatedAt());
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
}
