package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

/**
 * One run of a checklist at a site (and optionally against an asset). Answers can be saved a few at a time,
 * so a long inspection survives a dropped connection. Completing requires every check to be answered; the
 * overall result is FAIL if any check failed. A failed check needs a comment saying what is wrong.
 */
@Entity
@Table(name = "facility_inspections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityInspection {

    public static final Set<String> OUTCOMES = Set.of("PASS", "FAIL", "NA");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    @Column(name = "template_name", nullable = false)
    private String templateName;
    @Column(name = "template_revision", nullable = false)
    private int templateRevision;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "inspector_user_id")
    private UUID inspectorUserId;
    @Column(name = "inspector_name")
    private String inspectorName;

    /** IN_PROGRESS, COMPLETED or CANCELLED. */
    @Column(nullable = false)
    private String status = "IN_PROGRESS";

    /** PASS or FAIL once completed. */
    @Column(length = 10)
    private String result;

    @Column(length = 2000)
    private String notes;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "inspection_id", nullable = false)
    @OrderBy("position ASC")
    private List<FacilityInspectionResult> results = new ArrayList<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FacilityInspection start(TenantId tenantId, FacilityInspectionTemplate template, UUID siteId, UUID assetId,
                                           UUID inspectorUserId, String inspectorName) {
        if (!template.isActive()) throw new IllegalStateException("This template is switched off — activate it or use another");
        FacilityInspection i = new FacilityInspection();
        i.tenantId = tenantId;
        i.templateId = template.getId();
        i.templateName = template.getName();
        i.templateRevision = template.getRevision();
        i.siteId = siteId;
        i.assetId = assetId;
        i.inspectorUserId = inspectorUserId;
        i.inspectorName = inspectorName;
        for (FacilityInspectionTemplateItem item : template.getItems()) i.results.add(FacilityInspectionResult.from(item));
        i.startedAt = Instant.now();
        i.createdAt = i.startedAt;
        i.updatedAt = i.startedAt;
        return i;
    }

    /** Save one answer. Safe to repeat or change while the inspection is still open. */
    public void answer(UUID itemId, String outcome, String comment) {
        requireInProgress();
        String o = outcome == null ? "" : outcome.trim().toUpperCase();
        if (!OUTCOMES.contains(o)) throw new IllegalArgumentException("outcome must be one of PASS, FAIL, NA");
        String c = comment == null || comment.isBlank() ? null : comment.trim();
        if ("FAIL".equals(o) && c == null) throw new IllegalArgumentException("Say what is wrong when a check fails");
        if (c != null && c.length() > 1000) throw new IllegalArgumentException("A comment can be at most 1000 characters");
        results.stream().filter(r -> r.getItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That check is not part of this inspection"))
                .answer(o, c);
        this.updatedAt = Instant.now();
    }

    public List<FacilityInspectionResult> unanswered() {
        return results.stream().filter(r -> r.getOutcome() == null).toList();
    }

    public List<FacilityInspectionResult> failures() {
        return results.stream().filter(FacilityInspectionResult::isFailed).toList();
    }

    public boolean hasCriticalFailure() {
        return results.stream().anyMatch(r -> r.isFailed() && r.isCritical());
    }

    public void complete(String notes) {
        requireInProgress();
        long open = unanswered().size();
        if (open > 0) throw new IllegalStateException(open + " check(s) are still unanswered");
        this.result = failures().isEmpty() ? "PASS" : "FAIL";
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void cancel(String reason) {
        requireInProgress();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to cancel an inspection");
        this.status = "CANCELLED";
        this.cancelReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    private void requireInProgress() {
        if (!"IN_PROGRESS".equals(status)) throw new IllegalStateException("This inspection is already " + status);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
