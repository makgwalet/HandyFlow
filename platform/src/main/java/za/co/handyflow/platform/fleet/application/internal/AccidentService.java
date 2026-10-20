package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.domain.model.Accident;
import za.co.handyflow.platform.fleet.domain.model.AccidentDetails;
import za.co.handyflow.platform.fleet.domain.model.AccidentEvidence;
import za.co.handyflow.platform.fleet.domain.model.AccidentEvidenceType;
import za.co.handyflow.platform.fleet.domain.model.AccidentStatus;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.AccidentEvidenceRepository;
import za.co.handyflow.platform.fleet.domain.repository.AccidentRepository;
import za.co.handyflow.platform.fleet.domain.repository.DriverRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleInsuranceRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.AccidentDetailsRequest;
import za.co.handyflow.platform.fleet.dto.AccidentEvidenceResponse;
import za.co.handyflow.platform.fleet.dto.AccidentResponse;
import za.co.handyflow.platform.fleet.dto.ChangeAccidentStatusRequest;
import za.co.handyflow.platform.fleet.dto.ReportAccidentRequest;
import za.co.handyflow.platform.fleet.dto.UpdateAccidentClaimRequest;
import za.co.handyflow.platform.fleet.dto.UpdateAccidentRepairRequest;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Accident reporting: report, edit, claim, repair, status, and the files kept with the case.
 * <p>
 * Rules: an accident belongs to one vehicle of the tenant; the time of the accident cannot be in the
 * future; a driver picked from the register fills in the name; the claim insurer and excess are
 * pre-filled from the vehicle's insurance profile; a case with injuries or a death is raised as
 * CRITICAL (in-app, email and SMS), anything else as a WARNING. Write-off is declared through the
 * vehicle lifecycle, not here. The state rules themselves live on {@link Accident}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccidentService {

    static final long MAX_EVIDENCE_BYTES = 15L * 1024 * 1024;
    static final int MAX_EVIDENCE_PER_ACCIDENT = 50;
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);
    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AccidentRepository accidentRepository;
    private final AccidentEvidenceRepository evidenceRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final VehicleInsuranceRepository insuranceRepository;
    private final AccidentReferenceGenerator referenceGenerator;
    private final FleetUploadSupport uploads;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public record EvidenceDownload(String fileName, String contentType, long sizeBytes, InputStream stream) {}

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AccidentResponse> list(TenantId tenantId, UUID vehicleId, AccidentStatus status, Pageable pageable) {
        Page<Accident> page;
        if (vehicleId != null && status != null) {
            page = accidentRepository.findByVehicleAndStatus(tenantId, vehicleId, status, pageable);
        } else if (vehicleId != null) {
            page = accidentRepository.findByVehicle(tenantId, vehicleId, pageable);
        } else if (status != null) {
            page = accidentRepository.findByStatus(tenantId, status, pageable);
        } else {
            page = accidentRepository.findAllForTenant(tenantId, pageable);
        }
        return page.map(a -> toResponse(a, tenantId));
    }

    @Transactional(readOnly = true)
    public AccidentResponse get(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id), tenantId);
    }

    // ── Report and edit ──────────────────────────────────────────────────

    @Transactional
    public AccidentResponse report(TenantId tenantId, UUID reportedBy, ReportAccidentRequest req) {
        Vehicle vehicle = vehicleRepository.findActiveById(tenantId, req.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", req.vehicleId().toString()));
        AccidentDetails details = toDetails(tenantId, req.details());

        Accident accident = Accident.report(tenantId, referenceGenerator.next(tenantId), vehicle.getId(),
                details, reportedBy);
        insuranceRepository.findByTenantIdAndVehicleId(tenantId, vehicle.getId())
                .filter(ins -> ins.isInsured())
                .ifPresent(ins -> accident.prefillClaimInsurer(ins.getInsurer(), ins.getExcess()));
        accidentRepository.save(accident);

        log.info("Accident reported ref={} vehicle={} tenant={}", accident.getReferenceNumber(), vehicle.getId(), tenantId);
        notifyReported(tenantId, vehicle, accident);
        return toResponse(accident, tenantId);
    }

    @Transactional
    public AccidentResponse updateDetails(TenantId tenantId, UUID id, AccidentDetailsRequest req) {
        Accident accident = find(tenantId, id);
        accident.applyDetails(toDetails(tenantId, req));
        accidentRepository.save(accident);
        return toResponse(accident, tenantId);
    }

    @Transactional
    public AccidentResponse updateClaim(TenantId tenantId, UUID id, UpdateAccidentClaimRequest req) {
        Accident accident = find(tenantId, id);
        accident.updateClaim(req.status(), blankToNull(req.insurer()), blankToNull(req.claimNumber()),
                req.excess(), blankToNull(req.assessorName()), req.assessmentDate(), req.submittedAt(),
                req.settlementAmount(), req.settledAt());
        accidentRepository.save(accident);
        return toResponse(accident, tenantId);
    }

    @Transactional
    public AccidentResponse updateRepair(TenantId tenantId, UUID id, UpdateAccidentRepairRequest req) {
        Accident accident = find(tenantId, id);
        accident.updateRepair(blankToNull(req.repairer()), req.repairCost(), req.returnedToServiceAt());
        accidentRepository.save(accident);
        return toResponse(accident, tenantId);
    }

    @Transactional
    public AccidentResponse changeStatus(TenantId tenantId, UUID id, ChangeAccidentStatusRequest req) {
        if (req.status() == AccidentStatus.WRITTEN_OFF) {
            throw new IllegalArgumentException(
                    "A write-off is declared on the vehicle (Lifecycle → Write off), which closes this case");
        }
        Accident accident = find(tenantId, id);
        accident.changeStatus(req.status(), blankToNull(req.note()));
        accidentRepository.save(accident);
        return toResponse(accident, tenantId);
    }

    // ── Evidence ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccidentEvidenceResponse> listEvidence(TenantId tenantId, UUID accidentId) {
        find(tenantId, accidentId);
        return evidenceRepository.findActiveByAccident(tenantId, accidentId).stream()
                .map(this::toEvidenceResponse).toList();
    }

    @Transactional
    public AccidentEvidenceResponse uploadEvidence(TenantId tenantId, UUID accidentId, UUID userId,
                                                   MultipartFile file, AccidentEvidenceType type, String description) {
        Accident accident = find(tenantId, accidentId);
        if (evidenceRepository.countActiveByAccident(tenantId, accidentId) >= MAX_EVIDENCE_PER_ACCIDENT) {
            throw new IllegalArgumentException("An accident can have at most " + MAX_EVIDENCE_PER_ACCIDENT + " files");
        }
        String contentType = uploads.validate(file, EVIDENCE_TYPES, MAX_EVIDENCE_BYTES);
        String key = uploads.store("fleet/" + tenantId.getValue() + "/" + accident.getVehicleId()
                + "/accidents/" + accidentId, file, contentType);
        AccidentEvidence ev = AccidentEvidence.create(tenantId, accidentId, type,
                truncate(blankToNull(description), 500), uploads.sanitiseFileName(file.getOriginalFilename()),
                contentType, file.getSize(), key, userId);
        evidenceRepository.save(ev);
        return toEvidenceResponse(ev);
    }

    /** The caller owns closing the stream. */
    @Transactional(readOnly = true)
    public EvidenceDownload downloadEvidence(TenantId tenantId, UUID accidentId, UUID evidenceId) {
        AccidentEvidence ev = findEvidence(tenantId, accidentId, evidenceId);
        return new EvidenceDownload(ev.getFileName(), ev.getContentType(), ev.getSizeBytes(),
                uploads.open(ev.getStorageKey()));
    }

    /** Soft delete: the row is hidden, the stored file is kept as evidence. */
    @Transactional
    public void deleteEvidence(TenantId tenantId, UUID accidentId, UUID evidenceId, UUID userId) {
        AccidentEvidence ev = findEvidence(tenantId, accidentId, evidenceId);
        ev.softDelete(userId);
        evidenceRepository.save(ev);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    AccidentDetails toDetails(TenantId tenantId, AccidentDetailsRequest r) {
        if (r.occurredAt().isAfter(Instant.now().plus(CLOCK_TOLERANCE))) {
            throw new IllegalArgumentException("The accident cannot be in the future");
        }
        UUID driverId = r.driverId();
        String driverName = blankToNull(r.driverName());
        if (driverId != null) {
            Driver driver = driverRepository.findActiveById(tenantId, driverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", r.driverId().toString()));
            driverName = driver.getFullName();
        }
        return new AccidentDetails(driverId, driverName, r.tripId(), r.occurredAt(),
                blankToNull(r.location()), r.weather(), r.roadCondition(),
                blankToNull(r.otherVehicleRegistration()), blankToNull(r.otherDriverName()),
                blankToNull(r.otherDriverContact()), blankToNull(r.otherInsurer()),
                blankToNull(r.policeCaseNumber()), blankToNull(r.sapsStation()), blankToNull(r.policeReference()),
                r.description().trim(), blankToNull(r.witnesses()),
                r.injuriesCount() == null ? 0 : r.injuriesCount(),
                r.fatalitiesCount() == null ? 0 : r.fatalitiesCount(),
                r.estimatedDamage(), r.thirdPartyDamage(),
                r.vehicleDrivable() == null || r.vehicleDrivable());
    }

    private void notifyReported(TenantId tenantId, Vehicle vehicle, Accident a) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        boolean people = a.getInjuriesCount() > 0 || a.getFatalitiesCount() > 0;
        NotificationRequest.Builder b = NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.ACCIDENT_REPORTED)
                .title("Accident " + a.getReferenceNumber() + ": " + vehicle.getRegistration())
                .message(vehicle.getRegistration() + " was in an accident"
                        + (a.getLocation() != null ? " at " + a.getLocation() : "") + "."
                        + (a.getFatalitiesCount() > 0 ? " Fatalities: " + a.getFatalitiesCount() + "." : "")
                        + (a.getInjuriesCount() > 0 ? " Injuries: " + a.getInjuriesCount() + "." : "")
                        + (a.isVehicleDrivable() ? "" : " The vehicle cannot be driven."))
                .actionUrl("/fleet/accidents/" + a.getId())
                .sourceModule("fleet")
                .sourceEntityId(a.getId().toString())
                .recipients(recipients);
        if (people) {
            b.severity(NotificationSeverity.CRITICAL)
                    .channels(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS));
        }
        notificationService.send(b.build());
    }

    private Accident find(TenantId tenantId, UUID id) {
        return accidentRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Accident", id.toString()));
    }

    private AccidentEvidence findEvidence(TenantId tenantId, UUID accidentId, UUID evidenceId) {
        return evidenceRepository.findActiveById(tenantId, accidentId, evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("AccidentEvidence", evidenceId.toString()));
    }

    private AccidentEvidenceResponse toEvidenceResponse(AccidentEvidence e) {
        return new AccidentEvidenceResponse(e.getId(), e.getAccidentId(), e.getType().name(), e.getDescription(),
                e.getFileName(), e.getContentType(), e.getSizeBytes(), e.getUploadedAt());
    }

    private AccidentResponse toResponse(Accident a, TenantId tenantId) {
        String reg = vehicleRepository.findActiveById(tenantId, a.getVehicleId())
                .map(Vehicle::getRegistration).orElse(null);
        long evidence = evidenceRepository.countActiveByAccident(tenantId, a.getId());
        return new AccidentResponse(a.getId(), a.getReferenceNumber(), a.getVehicleId(), reg,
                a.getDriverId(), a.getDriverName(), a.getTripId(), a.getOccurredAt(), a.getLocation(),
                a.getWeather() == null ? null : a.getWeather().name(),
                a.getRoadCondition() == null ? null : a.getRoadCondition().name(),
                a.getOtherVehicleRegistration(), a.getOtherDriverName(), a.getOtherDriverContact(),
                a.getOtherInsurer(), a.getPoliceCaseNumber(), a.getSapsStation(), a.getPoliceReference(),
                a.getDescription(), a.getWitnesses(), a.getInjuriesCount(), a.getFatalitiesCount(),
                a.getEstimatedDamage(), a.getThirdPartyDamage(), a.isVehicleDrivable(),
                a.getStatus().name(), a.getResolutionNote(), a.getClosedAt(),
                a.getClaimStatus().name(), a.getClaimInsurer(), a.getClaimNumber(), a.getClaimExcess(),
                a.getAssessorName(), a.getAssessmentDate(), a.getClaimSubmittedAt(),
                a.getClaimSettlementAmount(), a.getClaimSettledAt(), a.getRepairer(), a.getRepairCost(),
                a.getReturnedToServiceAt(), evidence, a.getCreatedAt(), a.getUpdatedAt());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
