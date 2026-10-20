package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.domain.model.Accident;
import za.co.handyflow.platform.fleet.domain.model.AccidentStatus;
import za.co.handyflow.platform.fleet.domain.model.AcquisitionMethod;
import za.co.handyflow.platform.fleet.domain.model.LifecycleEventType;
import za.co.handyflow.platform.fleet.domain.model.LifecycleEvidence;
import za.co.handyflow.platform.fleet.domain.model.LifecycleEvidenceType;
import za.co.handyflow.platform.fleet.domain.model.RecoveryStatus;
import za.co.handyflow.platform.fleet.domain.model.TheftKind;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleDisposal;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleEvent;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleTheftReport;
import za.co.handyflow.platform.fleet.domain.model.VehicleWriteOff;
import za.co.handyflow.platform.fleet.domain.repository.AccidentRepository;
import za.co.handyflow.platform.fleet.domain.repository.LifecycleEvidenceRepository;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleDisposalRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleLifecycleEventRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleTheftReportRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleWriteOffRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * Asset lifecycle workflows: simple moves, theft / missing and recovery, write-off,
 * and sale / transfer / disposal. Every change writes a {@link VehicleLifecycleEvent}
 * (the audit trail) and, where relevant, a detail record hung off that event.
 * <p>
 * Rules worth knowing:
 * <ul>
 *   <li>Leaving the fleet (sold, transferred, written off, retired, disposed) is blocked
 *       while the vehicle is on a trip — end the trip first. Theft is not, because a
 *       vehicle can be stolen or hijacked mid-trip.</li>
 *   <li>A financed vehicle cannot be sold or transferred until finance is confirmed cleared;
 *       a leased / rented vehicle cannot be "sold" (record its return as a transfer).</li>
 *   <li>Writing off a vehicle linked to an accident also moves that accident case to WRITTEN_OFF.</li>
 *   <li>Ledger posting is not done here — record its reference in {@code accountingReference}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleLifecycleService {

    /** Moves the generic transition endpoint may make. Everything else has a dedicated workflow. */
    private static final Set<VehicleLifecycleStatus> GENERIC_TARGETS = EnumSet.of(
            VehicleLifecycleStatus.ACQUIRED, VehicleLifecycleStatus.ACTIVE,
            VehicleLifecycleStatus.IMPOUNDED, VehicleLifecycleStatus.RETIRED);

    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final long MAX_EVIDENCE_BYTES = 15L * 1024 * 1024;
    private static final int MAX_EVIDENCE_PER_EVENT = 50;

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final AccidentRepository accidentRepository;
    private final VehicleLifecycleEventRepository eventRepository;
    private final VehicleDisposalRepository disposalRepository;
    private final VehicleTheftReportRepository theftRepository;
    private final VehicleWriteOffRepository writeOffRepository;
    private final LifecycleEvidenceRepository evidenceRepository;
    private final FleetUploadSupport uploads;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public record EvidenceDownload(String fileName, String contentType, long sizeBytes, InputStream stream) {}

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VehicleLifecycleResponse get(TenantId tenantId, UUID vehicleId) {
        return toResponse(findVehicle(tenantId, vehicleId), tenantId);
    }

    // ── Simple transitions ───────────────────────────────────────────────

    @Transactional
    public VehicleLifecycleResponse transition(TenantId tenantId, UUID vehicleId, UUID userId,
                                               LifecycleTransitionRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        VehicleLifecycleStatus target = req.target();
        if (!GENERIC_TARGETS.contains(target)) {
            throw new IllegalArgumentException(target + " cannot be set here — use the dedicated "
                    + "theft / write-off / disposal endpoint, which records the required details");
        }
        VehicleLifecycleStatus current = vehicle.getLifecycleStatus();
        if (target == VehicleLifecycleStatus.ACTIVE
                && (current == VehicleLifecycleStatus.STOLEN || current == VehicleLifecycleStatus.MISSING)) {
            throw new IllegalArgumentException("Use the recovery endpoint to return a stolen or missing vehicle to service");
        }
        if (target.isOutOfFleet()) {
            requireNotOnTrip(vehicle);
        }
        record(vehicle, LifecycleEventType.STATUS_CHANGE, target, Instant.now(),
                blankToNull(req.reason()), blankToNull(req.note()), userId);
        return toResponse(vehicle, tenantId);
    }

    // ── Theft / missing / recovery ───────────────────────────────────────

    @Transactional
    public VehicleLifecycleResponse reportTheft(TenantId tenantId, UUID vehicleId, UUID userId, ReportTheftRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        requireNotFuture(req.occurredAt(), "The theft / last-seen time");

        VehicleLifecycleStatus target = req.kind() == TheftKind.STOLEN
                ? VehicleLifecycleStatus.STOLEN : VehicleLifecycleStatus.MISSING;
        boolean upgradingMissing = vehicle.getLifecycleStatus() == VehicleLifecycleStatus.MISSING
                && target == VehicleLifecycleStatus.STOLEN;

        VehicleLifecycleEvent event = record(vehicle, LifecycleEventType.THEFT_REPORTED, target, req.occurredAt(),
                req.kind() == TheftKind.STOLEN ? "Reported stolen" : "Reported missing", null, userId);

        if (upgradingMissing) {
            // The earlier MISSING report is replaced by this STOLEN one: only one open report per vehicle.
            theftRepository.findByVehicleAndRecoveryStatus(tenantId, vehicleId, RecoveryStatus.NOT_RECOVERED)
                    .forEach(r -> { r.supersede(); theftRepository.save(r); });
        }
        theftRepository.save(VehicleTheftReport.create(tenantId, vehicleId, event.getId(), req.kind(),
                req.occurredAt(), blankToNull(req.location()), blankToNull(req.policeCaseNumber()),
                blankToNull(req.sapsStation()), blankToNull(req.policeReference()), blankToNull(req.insurer()),
                blankToNull(req.claimNumber()), blankToNull(req.trackingInfo()), blankToNull(req.notes())));

        notifyTheft(tenantId, vehicle, req);
        log.info("Vehicle {} reported {} tenant={}", vehicleId, req.kind(), tenantId);
        return toResponse(vehicle, tenantId);
    }

    /** Police, insurer and tracking details usually trickle in after the report. */
    @Transactional
    public VehicleLifecycleResponse updateTheftReport(TenantId tenantId, UUID vehicleId, UpdateTheftReportRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        VehicleTheftReport report = openTheftReport(tenantId, vehicleId);
        report.updateDetails(blankToNull(req.location()), blankToNull(req.policeCaseNumber()),
                blankToNull(req.sapsStation()), blankToNull(req.policeReference()), blankToNull(req.insurer()),
                blankToNull(req.claimNumber()), blankToNull(req.trackingInfo()), blankToNull(req.notes()));
        theftRepository.save(report);
        return toResponse(vehicle, tenantId);
    }

    @Transactional
    public VehicleLifecycleResponse recordRecovery(TenantId tenantId, UUID vehicleId, UUID userId,
                                                   RecordRecoveryRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        VehicleLifecycleStatus current = vehicle.getLifecycleStatus();
        if (current != VehicleLifecycleStatus.STOLEN && current != VehicleLifecycleStatus.MISSING) {
            throw new IllegalStateException("Vehicle is " + current + " — only a STOLEN or MISSING vehicle can be recovered");
        }
        requireNotFuture(req.recoveredAt(), "The recovery time");

        VehicleTheftReport report = openTheftReport(tenantId, vehicleId);
        report.markRecovered(req.recoveryStatus(), req.recoveredAt(), blankToNull(req.location()),
                blankToNull(req.notes()));
        theftRepository.save(report);

        if (req.odometer() != null) {
            vehicle.updateOdometer(req.odometer());   // floor-guarded
        }
        record(vehicle, LifecycleEventType.RECOVERED, VehicleLifecycleStatus.ACTIVE, req.recoveredAt(),
                req.recoveryStatus() == RecoveryStatus.RECOVERED_DAMAGED ? "Recovered damaged" : "Recovered",
                blankToNull(req.notes()), userId);
        return toResponse(vehicle, tenantId);
    }

    // ── Write-off ────────────────────────────────────────────────────────

    @Transactional
    public VehicleLifecycleResponse recordWriteOff(TenantId tenantId, UUID vehicleId, UUID userId,
                                                   RecordWriteOffRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        requireNotOnTrip(vehicle);
        if (req.decisionDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The write-off decision date cannot be in the future");
        }

        Accident accident = null;
        if (req.accidentId() != null) {
            accident = accidentRepository.findByTenantAndId(tenantId, req.accidentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Accident", req.accidentId().toString()));
            if (!accident.getVehicleId().equals(vehicleId)) {
                throw new IllegalArgumentException("That accident belongs to a different vehicle");
            }
        }

        VehicleLifecycleEvent event = record(vehicle, LifecycleEventType.WRITE_OFF,
                VehicleLifecycleStatus.WRITTEN_OFF, req.decisionDate().atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).toInstant(),
                "Declared a write-off", blankToNull(req.decisionNote()), userId);

        java.math.BigDecimal preValue = req.preAccidentValue() != null ? req.preAccidentValue()
                : (vehicle.getCurrentEstimatedValue() != null ? vehicle.getCurrentEstimatedValue()
                : vehicle.getCurrentBookValue());
        writeOffRepository.save(VehicleWriteOff.create(tenantId, vehicleId, event.getId(), req.accidentId(),
                req.assessmentDate(), blankToNull(req.assessorName()), req.decisionDate(),
                blankToNull(req.decisionNote()), preValue, req.salvageValue(), blankToNull(req.salvageBuyer()),
                req.salvageSoldAt(), req.insuranceSettlementAmount(), req.insuranceSettledAt()));

        if (accident != null && accident.getStatus() != AccidentStatus.WRITTEN_OFF) {
            // Throws (409) if the case can't legally move there, e.g. it is CLOSED — reopen it first.
            accident.changeStatus(AccidentStatus.WRITTEN_OFF, "Vehicle declared a write-off on " + req.decisionDate());
            accidentRepository.save(accident);
        }
        log.info("Vehicle {} written off tenant={}", vehicleId, tenantId);
        return toResponse(vehicle, tenantId);
    }

    /** Salvage and insurance-settlement details are usually known later; editable while WRITTEN_OFF. */
    @Transactional
    public VehicleLifecycleResponse updateWriteOff(TenantId tenantId, UUID vehicleId, RecordWriteOffRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        if (vehicle.getLifecycleStatus() != VehicleLifecycleStatus.WRITTEN_OFF) {
            throw new IllegalStateException("Vehicle is " + vehicle.getLifecycleStatus()
                    + " — write-off details can only be edited while it is WRITTEN_OFF");
        }
        VehicleWriteOff w = writeOffRepository.findByVehicle(tenantId, vehicleId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("VehicleWriteOff", vehicleId.toString()));
        w.update(req.assessmentDate(), blankToNull(req.assessorName()), req.decisionDate(),
                blankToNull(req.decisionNote()), req.preAccidentValue(), req.salvageValue(),
                blankToNull(req.salvageBuyer()), req.salvageSoldAt(), req.insuranceSettlementAmount(),
                req.insuranceSettledAt());
        writeOffRepository.save(w);
        return toResponse(vehicle, tenantId);
    }

    // ── Sale / transfer / disposal ───────────────────────────────────────

    @Transactional
    public VehicleLifecycleResponse recordDisposal(TenantId tenantId, UUID vehicleId, UUID userId,
                                                   RecordDisposalRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        VehicleLifecycleStatus target = req.target();
        if (target != VehicleLifecycleStatus.SOLD && target != VehicleLifecycleStatus.TRANSFERRED
                && target != VehicleLifecycleStatus.DISPOSED) {
            throw new IllegalArgumentException("Disposal target must be SOLD, TRANSFERRED or DISPOSED");
        }
        if (req.disposalDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The disposal date cannot be in the future");
        }
        validateDisposal(vehicle, req);
        requireNotOnTrip(vehicle);

        if (req.odometerAtDisposal() != null) {
            vehicle.updateOdometer(req.odometerAtDisposal());   // floor-guarded
        }
        VehicleLifecycleEvent event = record(vehicle, LifecycleEventType.DISPOSAL, target,
                req.disposalDate().atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).toInstant(),
                req.method() != null ? req.method().name() : target.name(), blankToNull(req.notes()), userId);

        disposalRepository.save(VehicleDisposal.create(tenantId, vehicleId, event.getId(), req.method(),
                blankToNull(req.counterpartyName()), blankToNull(req.counterpartyContact()), req.disposalDate(),
                req.salePrice(), req.odometerAtDisposal(), vehicle.getCurrentBookValue(), req.financeCleared(),
                req.financeSettlementAmount(), req.registrationTransferred(),
                req.registrationTransferred() && req.registrationTransferDate() == null
                        ? LocalDate.now() : req.registrationTransferDate(),
                blankToNull(req.accountingReference()), blankToNull(req.notes())));
        log.info("Vehicle {} disposed target={} tenant={}", vehicleId, target, tenantId);
        return toResponse(vehicle, tenantId);
    }

    private void validateDisposal(Vehicle vehicle, RecordDisposalRequest req) {
        VehicleLifecycleStatus target = req.target();
        boolean needsCounterparty = target == VehicleLifecycleStatus.SOLD || target == VehicleLifecycleStatus.TRANSFERRED;
        if (needsCounterparty && blankToNull(req.counterpartyName()) == null) {
            throw new IllegalArgumentException("The buyer / transferee name is required");
        }
        if (target == VehicleLifecycleStatus.SOLD) {
            if (req.salePrice() == null) {
                throw new IllegalArgumentException("A sale price is required when selling a vehicle");
            }
            AcquisitionMethod m = vehicle.getAcquisitionMethod();
            if (m == AcquisitionMethod.LEASED || m == AcquisitionMethod.RENTED) {
                throw new IllegalArgumentException("A " + m.name().toLowerCase()
                        + " vehicle is not owned and cannot be sold — record its return as a TRANSFERRED disposal");
            }
        }
        if (needsCounterparty && vehicle.getAcquisitionMethod() == AcquisitionMethod.FINANCED && !req.financeCleared()) {
            throw new IllegalArgumentException("This vehicle is financed — confirm the finance has been "
                    + "settled (financeCleared) before selling or transferring it");
        }
    }

    // ── Evidence ─────────────────────────────────────────────────────────

    @Transactional
    public LifecycleEvidenceResponse uploadEvidence(TenantId tenantId, UUID vehicleId, UUID eventId, UUID userId,
                                                    MultipartFile file, LifecycleEvidenceType type,
                                                    String description) {
        findVehicle(tenantId, vehicleId);
        findEvent(tenantId, vehicleId, eventId);
        if (evidenceRepository.countActiveByEvent(tenantId, eventId) >= MAX_EVIDENCE_PER_EVENT) {
            throw new IllegalArgumentException("An event can have at most " + MAX_EVIDENCE_PER_EVENT + " evidence files");
        }
        String contentType = uploads.validate(file, EVIDENCE_TYPES, MAX_EVIDENCE_BYTES);
        String key = uploads.store("fleet/" + tenantId.getValue() + "/" + vehicleId + "/lifecycle/" + eventId, file, contentType);

        LifecycleEvidence ev = LifecycleEvidence.create(tenantId, vehicleId, eventId, type,
                truncate(blankToNull(description), 500), uploads.sanitiseFileName(file.getOriginalFilename()),
                contentType, file.getSize(), key, userId);
        evidenceRepository.save(ev);
        return toEvidenceResponse(ev);
    }

    @Transactional(readOnly = true)
    public EvidenceDownload downloadEvidence(TenantId tenantId, UUID vehicleId, UUID eventId, UUID evidenceId) {
        LifecycleEvidence ev = evidenceRepository.findActiveById(tenantId, vehicleId, eventId, evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("LifecycleEvidence", evidenceId.toString()));
        return new EvidenceDownload(ev.getFileName(), ev.getContentType(), ev.getSizeBytes(),
                uploads.open(ev.getStorageKey()));
    }

    /** Soft delete — hidden from the history, stored file retained. */
    @Transactional
    public void deleteEvidence(TenantId tenantId, UUID vehicleId, UUID eventId, UUID evidenceId, UUID deletedBy) {
        LifecycleEvidence ev = evidenceRepository.findActiveById(tenantId, vehicleId, eventId, evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("LifecycleEvidence", evidenceId.toString()));
        ev.softDelete(deletedBy);
        evidenceRepository.save(ev);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Applies the lifecycle change to the vehicle (validating the transition) and writes the audit event. */
    private VehicleLifecycleEvent record(Vehicle vehicle, LifecycleEventType type, VehicleLifecycleStatus target,
                                         Instant occurredAt, String reason, String note, UUID userId) {
        VehicleLifecycleStatus from = vehicle.getLifecycleStatus();
        vehicle.changeLifecycleStatus(target);
        vehicleRepository.save(vehicle);
        return eventRepository.save(VehicleLifecycleEvent.record(vehicle.getTenantId(), vehicle.getId(), type,
                from, target, occurredAt, reason, note, userId));
    }

    private void requireNotOnTrip(Vehicle vehicle) {
        if (vehicle.getStatus() == VehicleStatus.ON_TRIP || tripRepository.findActiveTrip(vehicle.getId()).isPresent()) {
            throw new IllegalStateException("Vehicle is on a trip — end the trip before taking it out of the fleet");
        }
    }

    private void requireNotFuture(Instant when, String what) {
        if (when.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException(what + " cannot be in the future");
        }
    }

    private VehicleTheftReport openTheftReport(TenantId tenantId, UUID vehicleId) {
        return theftRepository.findByVehicleAndRecoveryStatus(tenantId, vehicleId, RecoveryStatus.NOT_RECOVERED)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("This vehicle has no open theft / missing report"));
    }

    private Vehicle findVehicle(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));
    }

    private VehicleLifecycleEvent findEvent(TenantId tenantId, UUID vehicleId, UUID eventId) {
        return eventRepository.findByVehicleAndId(tenantId, vehicleId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("VehicleLifecycleEvent", eventId.toString()));
    }

    private void notifyTheft(TenantId tenantId, Vehicle vehicle, ReportTheftRequest req) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        String verb = req.kind() == TheftKind.STOLEN ? "reported stolen" : "reported missing";
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.VEHICLE_THEFT_REPORTED)
                .title("Vehicle " + verb + ": " + vehicle.getRegistration())
                .message(vehicle.getRegistration() + " (" + vehicle.getMake() + " " + vehicle.getModel() + ") was "
                        + verb + (req.location() != null ? " — " + req.location() : "") + ".")
                .actionUrl("/fleet/vehicles/" + vehicle.getId())
                .sourceModule("fleet")
                .sourceEntityId(vehicle.getId().toString())
                .recipients(recipients)
                .build());
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private LifecycleEvidenceResponse toEvidenceResponse(LifecycleEvidence e) {
        return new LifecycleEvidenceResponse(e.getId(), e.getEventId(), e.getType().name(), e.getDescription(),
                e.getFileName(), e.getContentType(), e.getSizeBytes(), e.getUploadedBy(), e.getUploadedAt());
    }

    private VehicleLifecycleResponse toResponse(Vehicle vehicle, TenantId tenantId) {
        UUID vehicleId = vehicle.getId();
        Map<UUID, VehicleDisposal> disposals = disposalRepository.findByVehicle(tenantId, vehicleId).stream()
                .collect(Collectors.toMap(VehicleDisposal::getEventId, Function.identity()));
        Map<UUID, VehicleTheftReport> thefts = theftRepository.findByVehicle(tenantId, vehicleId).stream()
                .collect(Collectors.toMap(VehicleTheftReport::getEventId, Function.identity()));
        Map<UUID, VehicleWriteOff> writeOffs = writeOffRepository.findByVehicle(tenantId, vehicleId).stream()
                .collect(Collectors.toMap(VehicleWriteOff::getEventId, Function.identity()));
        Map<UUID, List<LifecycleEvidenceResponse>> evidence = evidenceRepository.findActiveByVehicle(tenantId, vehicleId)
                .stream().map(this::toEvidenceResponse)
                .collect(Collectors.groupingBy(LifecycleEvidenceResponse::eventId));

        List<LifecycleEventResponse> events = eventRepository.findByVehicle(tenantId, vehicleId).stream()
                .map(e -> new LifecycleEventResponse(e.getId(), e.getEventType().name(),
                        e.getFromStatus().name(), e.getToStatus().name(), e.getOccurredAt(), e.getRecordedAt(),
                        e.getReason(), e.getNote(), e.getRecordedBy(),
                        disposals.containsKey(e.getId()) ? toDisposal(disposals.get(e.getId())) : null,
                        thefts.containsKey(e.getId()) ? toTheft(thefts.get(e.getId())) : null,
                        writeOffs.containsKey(e.getId()) ? toWriteOff(writeOffs.get(e.getId())) : null,
                        evidence.getOrDefault(e.getId(), List.of())))
                .toList();
        return new VehicleLifecycleResponse(vehicleId, vehicle.getRegistration(),
                vehicle.getLifecycleStatus().name(), vehicle.getStatus().name(), events);
    }

    private DisposalResponse toDisposal(VehicleDisposal d) {
        return new DisposalResponse(d.getDisposalMethod() != null ? d.getDisposalMethod().name() : null,
                d.getCounterpartyName(), d.getCounterpartyContact(), d.getDisposalDate(), d.getSalePrice(),
                d.getOdometerAtDisposal(), d.getBookValueAtDisposal(), d.getGainLoss(), d.isFinanceCleared(),
                d.getFinanceSettlementAmount(), d.isRegistrationTransferred(), d.getRegistrationTransferDate(),
                d.getAccountingReference(), d.getNotes());
    }

    private TheftReportResponse toTheft(VehicleTheftReport r) {
        return new TheftReportResponse(r.getKind().name(), r.getOccurredAt(), r.getLocation(),
                r.getPoliceCaseNumber(), r.getSapsStation(), r.getPoliceReference(), r.getInsurer(),
                r.getClaimNumber(), r.getTrackingInfo(), r.getNotes(), r.getRecoveryStatus().name(),
                r.getRecoveredAt(), r.getRecoveryLocation(), r.getRecoveryNotes());
    }

    private WriteOffResponse toWriteOff(VehicleWriteOff w) {
        return new WriteOffResponse(w.getAccidentId(), w.getAssessmentDate(), w.getAssessorName(),
                w.getDecisionDate(), w.getDecisionNote(), w.getPreAccidentValue(), w.getSalvageValue(),
                w.getSalvageBuyer(), w.getSalvageSoldAt(), w.getInsuranceSettlementAmount(),
                w.getInsuranceSettledAt());
    }
}