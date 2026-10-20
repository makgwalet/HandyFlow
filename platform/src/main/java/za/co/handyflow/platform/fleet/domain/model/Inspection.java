package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One performed inspection of one vehicle. Lifecycle: IN_PROGRESS → COMPLETED (or
 * CANCELLED). Once completed it is immutable — it is evidence.
 * <p>
 * Template name, type, signature requirement and every question are copied in when the
 * inspection starts, so later template edits never rewrite history.
 */
@Entity
@Table(name = "fleet_inspections")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Inspection {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false, length = 30)
    private InspectionType inspectionType;

    @Column(name = "signature_required", nullable = false)
    private boolean signatureRequired;

    @Column(name = "inspector_name")
    private String inspectorName;

    @Column(name = "inspector_id")
    private UUID inspectorId;

    @Column(name = "driver_id")
    private UUID driverId;

    private Integer odometer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionStatus status = InspectionStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private InspectionVerdict verdict;

    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "signature_key", length = 500)
    private String signatureKey;

    @Column(name = "signature_content_type", length = 100)
    private String signatureContentType;

    @Column(name = "signer_name")
    private String signerName;

    @Column(name = "signed_at")
    private Instant signedAt;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<InspectionResult> results = new ArrayList<>();

    @Version
    private Long version;

    public static Inspection start(TenantId tenantId, UUID vehicleId, InspectionTemplate template,
                                   String inspectorName, UUID inspectorId, UUID driverId, Integer odometer) {
        Inspection i = new Inspection();
        i.tenantId = tenantId;
        i.vehicleId = vehicleId;
        i.templateId = template.getId();
        i.templateName = template.getName();
        i.inspectionType = template.getInspectionType();
        i.signatureRequired = template.isSignatureRequired();
        i.inspectorName = inspectorName;
        i.inspectorId = inspectorId;
        i.driverId = driverId;
        i.odometer = odometer;
        i.startedAt = Instant.now();
        for (InspectionTemplateItem item : template.getItems()) {
            i.results.add(InspectionResult.from(i, item));
        }
        return i;
    }

    public void answer(UUID resultId, InspectionOutcome outcome, String comment) {
        requireInProgress();
        if (outcome == InspectionOutcome.PENDING) {
            throw new IllegalArgumentException("An answer must be PASS, FAIL or NOT_APPLICABLE");
        }
        findResult(resultId).answer(outcome, comment);
    }

    public InspectionResult findResult(UUID resultId) {
        return results.stream()
                .filter(r -> r.getId().equals(resultId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Checklist item " + resultId + " does not belong to this inspection"));
    }

    public void attachSignature(String key, String contentType, String signerName) {
        requireInProgress();
        this.signatureKey = key;
        this.signatureContentType = contentType;
        this.signerName = signerName;
        this.signedAt = Instant.now();
    }

    /**
     * Completes the inspection. Rules: every required item answered; every failed item
     * that demands a photo has one; a signature is on file if the template required it.
     * Unanswered optional items become NOT_APPLICABLE.
     */
    public void complete(UUID completedBy, String notes, Set<UUID> resultIdsWithPhotos) {
        requireInProgress();

        long unanswered = results.stream()
                .filter(r -> r.isRequired() && r.getOutcome() == InspectionOutcome.PENDING).count();
        if (unanswered > 0) {
            throw new IllegalArgumentException(unanswered + " required checklist item(s) have not been answered");
        }
        List<String> missingPhotos = results.stream()
                .filter(r -> r.getOutcome() == InspectionOutcome.FAIL && r.isPhotoRequiredOnFail()
                        && !resultIdsWithPhotos.contains(r.getId()))
                .map(InspectionResult::getLabel)
                .toList();
        if (!missingPhotos.isEmpty()) {
            throw new IllegalArgumentException(
                    "A photo is required for failed item(s): " + String.join(", ", missingPhotos));
        }
        if (signatureRequired && signatureKey == null) {
            throw new IllegalArgumentException("A signature is required to complete this inspection");
        }

        results.stream().filter(r -> r.getOutcome() == InspectionOutcome.PENDING)
                .forEach(InspectionResult::markNotApplicable);

        this.failedCount = (int) results.stream().filter(r -> r.getOutcome() == InspectionOutcome.FAIL).count();
        this.verdict = failedCount > 0 ? InspectionVerdict.FAIL : InspectionVerdict.PASS;
        this.notes = notes;
        this.completedBy = completedBy;
        this.completedAt = Instant.now();
        this.status = InspectionStatus.COMPLETED;
    }

    public void cancel() {
        requireInProgress();
        this.status = InspectionStatus.CANCELLED;
    }

    public List<InspectionResult> getFailedResults() {
        return results.stream().filter(r -> r.getOutcome() == InspectionOutcome.FAIL).toList();
    }

    public boolean hasSignature() { return signatureKey != null; }

    private void requireInProgress() {
        if (status != InspectionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Inspection is " + status + " and can no longer be changed");
        }
    }
}