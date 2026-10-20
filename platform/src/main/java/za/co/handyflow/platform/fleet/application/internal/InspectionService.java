package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.domain.model.Inspection;
import za.co.handyflow.platform.fleet.domain.model.InspectionPhoto;
import za.co.handyflow.platform.fleet.domain.model.InspectionResult;
import za.co.handyflow.platform.fleet.domain.model.InspectionStatus;
import za.co.handyflow.platform.fleet.domain.model.InspectionTemplate;
import za.co.handyflow.platform.fleet.domain.model.IssueSeverity;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.InspectionPhotoRepository;
import za.co.handyflow.platform.fleet.domain.repository.InspectionRepository;
import za.co.handyflow.platform.fleet.domain.repository.InspectionTemplateRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs vehicle inspections and raises the failed-inspection notification.
 * <p>
 * Inspection flow: start (questions copied from the template) → save answers → attach
 * photos / signature → complete. Completing validates the rules, locks the inspection,
 * turns each FAIL into a {@code FleetIssue} (skipping a defect already open on the same
 * vehicle) and notifies tenant admins if anything failed.
 * <p>
 * A failed inspection does not change the vehicle's status automatically — whether a
 * defect takes a vehicle off the road is a judgement call; use the status endpoint
 * (AVAILABLE → MAINTENANCE) for that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionService {

    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final long MAX_SIGNATURE_BYTES = 1L * 1024 * 1024;
    private static final int MAX_PHOTOS_PER_INSPECTION = 50;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Africa/Johannesburg"));

    private final VehicleRepository vehicleRepository;
    private final InspectionTemplateRepository templateRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionPhotoRepository photoRepository;
    private final FleetIssueService issueService;
    private final FleetUploadSupport uploads;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public record FileDownload(String fileName, String contentType, long sizeBytes, InputStream stream) {}

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Transactional
    public InspectionDetailResponse start(TenantId tenantId, UUID vehicleId, UUID userId, StartInspectionRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        if (vehicle.getStatus() == VehicleStatus.RETIRED) {
            throw new IllegalArgumentException("A retired vehicle cannot be inspected");
        }
        InspectionTemplate template = templateRepository.findActiveById(tenantId, req.templateId())
                .orElseThrow(() -> new ResourceNotFoundException("InspectionTemplate", req.templateId().toString()));
        if (!template.isActive()) {
            throw new IllegalArgumentException("Inspection template \"" + template.getName() + "\" is inactive");
        }

        if (req.odometer() != null) {
            // Floor-guarded: rejects a reading lower than the vehicle's current odometer.
            vehicle.updateOdometer(req.odometer());
            vehicleRepository.save(vehicle);
        }

        Inspection inspection = Inspection.start(tenantId, vehicleId, template,
                blankToNull(req.inspectorName()), userId, req.driverId(), req.odometer());
        inspectionRepository.save(inspection);
        log.info("Inspection started id={} vehicle={} template={} tenant={}",
                inspection.getId(), vehicleId, template.getName(), tenantId);
        return toDetail(inspection, vehicle.getRegistration(), tenantId);
    }

    @Transactional
    public InspectionDetailResponse saveAnswers(TenantId tenantId, UUID inspectionId, SaveInspectionAnswersRequest req) {
        Inspection inspection = find(tenantId, inspectionId);
        for (InspectionAnswerRequest a : req.answers()) {
            inspection.answer(a.resultId(), a.outcome(), blankToNull(a.comment()));
        }
        inspectionRepository.save(inspection);
        return toDetail(inspection, registrationOf(tenantId, inspection.getVehicleId()), tenantId);
    }

    @Transactional
    public InspectionDetailResponse complete(TenantId tenantId, UUID inspectionId, UUID userId,
                                             CompleteInspectionRequest req) {
        Inspection inspection = find(tenantId, inspectionId);

        Set<UUID> resultsWithPhotos = photoRepository.findByInspection(tenantId, inspectionId).stream()
                .map(InspectionPhoto::getResultId).filter(Objects::nonNull).collect(Collectors.toSet());
        inspection.complete(userId, blankToNull(req == null ? null : req.notes()), resultsWithPhotos);
        inspectionRepository.save(inspection);

        Vehicle vehicle = findVehicle(tenantId, inspection.getVehicleId());
        int raised = 0;
        for (InspectionResult r : inspection.getFailedResults()) {
            String title = r.getSection() + " — " + r.getLabel();
            String description = "Failed on the \"" + inspection.getTemplateName() + "\" inspection of "
                    + DATE_FMT.format(inspection.getCompletedAt())
                    + (r.getComment() != null ? ": " + r.getComment() : ".");
            if (issueService.raiseFromInspection(tenantId, vehicle.getId(), inspection.getId(), r.getId(),
                    title, description, r.getSeverity(), userId)) {
                raised++;
            }
        }
        if (inspection.getFailedCount() > 0) {
            notifyFailed(tenantId, vehicle, inspection, raised);
        }
        log.info("Inspection completed id={} vehicle={} verdict={} failed={} newIssues={}",
                inspectionId, vehicle.getId(), inspection.getVerdict(), inspection.getFailedCount(), raised);
        return toDetail(inspection, vehicle.getRegistration(), tenantId);
    }

    @Transactional
    public InspectionDetailResponse cancel(TenantId tenantId, UUID inspectionId) {
        Inspection inspection = find(tenantId, inspectionId);
        inspection.cancel();
        inspectionRepository.save(inspection);
        return toDetail(inspection, registrationOf(tenantId, inspection.getVehicleId()), tenantId);
    }

    // ── Photos & signature ───────────────────────────────────────────────

    @Transactional
    public InspectionPhotoResponse uploadPhoto(TenantId tenantId, UUID inspectionId, UUID userId,
                                               MultipartFile file, UUID resultId, String caption) {
        Inspection inspection = find(tenantId, inspectionId);
        requireInProgress(inspection);
        if (resultId != null) {
            inspection.findResult(resultId);   // throws if it isn't one of this inspection's items
        }
        if (photoRepository.countByInspection(tenantId, inspectionId) >= MAX_PHOTOS_PER_INSPECTION) {
            throw new IllegalArgumentException("An inspection can have at most " + MAX_PHOTOS_PER_INSPECTION + " photos");
        }

        String contentType = uploads.validate(file, FleetUploadSupport.IMAGE_TYPES, MAX_PHOTO_BYTES);
        String key = storageKey(tenantId, inspection) + UUID.randomUUID();
        uploads.store(key, file, contentType);

        InspectionPhoto photo = InspectionPhoto.create(tenantId, inspectionId, resultId,
                uploads.sanitiseFileName(file.getOriginalFilename()), contentType, file.getSize(),
                key, truncate(blankToNull(caption), 500), userId);
        photoRepository.save(photo);
        return toPhotoResponse(photo);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadPhoto(TenantId tenantId, UUID inspectionId, UUID photoId) {
        find(tenantId, inspectionId);
        InspectionPhoto p = photoRepository.findByTenantAndId(tenantId, inspectionId, photoId)
                .orElseThrow(() -> new ResourceNotFoundException("InspectionPhoto", photoId.toString()));
        return new FileDownload(p.getFileName(), p.getContentType(), p.getSizeBytes(), uploads.open(p.getStorageKey()));
    }

    @Transactional
    public InspectionDetailResponse uploadSignature(TenantId tenantId, UUID inspectionId,
                                                    MultipartFile file, String signerName) {
        Inspection inspection = find(tenantId, inspectionId);
        requireInProgress(inspection);
        if (blankToNull(signerName) == null) {
            throw new IllegalArgumentException("The signer's name is required");
        }
        String contentType = uploads.validate(file, FleetUploadSupport.SIGNATURE_TYPES, MAX_SIGNATURE_BYTES);
        String key = storageKey(tenantId, inspection) + "signature-" + UUID.randomUUID();
        uploads.store(key, file, contentType);

        inspection.attachSignature(key, contentType, signerName.trim());
        inspectionRepository.save(inspection);
        return toDetail(inspection, registrationOf(tenantId, inspection.getVehicleId()), tenantId);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadSignature(TenantId tenantId, UUID inspectionId) {
        Inspection inspection = find(tenantId, inspectionId);
        if (!inspection.hasSignature()) {
            throw new ResourceNotFoundException("InspectionSignature", inspectionId.toString());
        }
        return new FileDownload("signature", inspection.getSignatureContentType(), -1,
                uploads.open(inspection.getSignatureKey()));
    }

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InspectionDetailResponse get(TenantId tenantId, UUID inspectionId) {
        Inspection inspection = find(tenantId, inspectionId);
        return toDetail(inspection, registrationOf(tenantId, inspection.getVehicleId()), tenantId);
    }

    @Transactional(readOnly = true)
    public Page<InspectionResponse> listForVehicle(TenantId tenantId, UUID vehicleId, Pageable pageable) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        return inspectionRepository.findByVehicle(tenantId, vehicleId, pageable)
                .map(i -> toSummary(i, vehicle.getRegistration()));
    }

    @Transactional(readOnly = true)
    public Page<InspectionResponse> listAll(TenantId tenantId, InspectionStatus status, Pageable pageable) {
        Page<Inspection> page = status != null
                ? inspectionRepository.findAllForTenantByStatus(tenantId, status, pageable)
                : inspectionRepository.findAllForTenant(tenantId, pageable);
        Map<UUID, String> regCache = new HashMap<>();
        return page.map(i -> toSummary(i,
                regCache.computeIfAbsent(i.getVehicleId(), id -> registrationOf(tenantId, id))));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Inspection find(TenantId tenantId, UUID id) {
        return inspectionRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection", id.toString()));
    }

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private String registrationOf(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId).map(Vehicle::getRegistration).orElse(null);
    }

    private void requireInProgress(Inspection inspection) {
        if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Inspection is " + inspection.getStatus() + " and can no longer be changed");
        }
    }

    private String storageKey(TenantId tenantId, Inspection inspection) {
        return "fleet/" + tenantId.getValue() + "/" + inspection.getVehicleId()
                + "/inspections/" + inspection.getId() + "/";
    }

    private void notifyFailed(TenantId tenantId, Vehicle vehicle, Inspection inspection, int newIssues) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        IssueSeverity worst = inspection.getFailedResults().stream()
                .map(InspectionResult::getSeverity)
                .max(Comparator.naturalOrder()).orElse(IssueSeverity.MEDIUM);
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INSPECTION_FAILED)
                .title("Inspection failed: " + vehicle.getRegistration())
                .message(vehicle.getRegistration() + " failed " + inspection.getFailedCount() + " item(s) on the \""
                        + inspection.getTemplateName() + "\" inspection (worst severity: " + worst + "). "
                        + newIssues + " new issue(s) logged.")
                .actionUrl("/fleet/inspections/" + inspection.getId())
                .sourceModule("fleet")
                .sourceEntityId(inspection.getId().toString())
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

    private InspectionResponse toSummary(Inspection i, String registration) {
        return new InspectionResponse(i.getId(), i.getVehicleId(), registration, i.getTemplateId(),
                i.getTemplateName(), i.getInspectionType().name(), i.getInspectorName(), i.getOdometer(),
                i.getStatus().name(), i.getVerdict() != null ? i.getVerdict().name() : null,
                i.getFailedCount(), i.getStartedAt(), i.getCompletedAt());
    }

    private InspectionPhotoResponse toPhotoResponse(InspectionPhoto p) {
        return new InspectionPhotoResponse(p.getId(), p.getResultId(), p.getFileName(), p.getContentType(),
                p.getSizeBytes(), p.getCaption(), p.getUploadedAt());
    }

    private InspectionDetailResponse toDetail(Inspection i, String registration, TenantId tenantId) {
        List<InspectionPhoto> photos = photoRepository.findByInspection(tenantId, i.getId());
        Map<UUID, List<UUID>> photoIdsByResult = photos.stream()
                .filter(p -> p.getResultId() != null)
                .collect(Collectors.groupingBy(InspectionPhoto::getResultId,
                        Collectors.mapping(InspectionPhoto::getId, Collectors.toList())));

        List<InspectionResultResponse> results = i.getResults().stream()
                .map(r -> new InspectionResultResponse(r.getId(), r.getSection(), r.getLabel(), r.getSortOrder(),
                        r.isRequired(), r.isPhotoRequiredOnFail(), r.getSeverity().name(),
                        r.getOutcome().name(), r.getComment(),
                        photoIdsByResult.getOrDefault(r.getId(), List.of())))
                .toList();

        return new InspectionDetailResponse(toSummary(i, registration), i.getDriverId(), i.getNotes(),
                i.isSignatureRequired(), i.hasSignature(), i.getSignerName(), i.getSignedAt(),
                results, photos.stream().map(this::toPhotoResponse).toList());
    }
}