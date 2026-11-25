package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A second person's check of a piece of work. It is completed once, as approved or sent back for rework; the next round is a new review.
 * A person who prepared the work reviews it only with a recorded reason.
 */
@Entity(name = "ComplianceReview")
@Table(name = "compliance_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReview {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "subject_type", nullable = false)
    private String subjectType;
    @Column(name = "subject_id")
    private UUID subjectId;
    @Column(name = "subject_label", nullable = false)
    private String subjectLabel;
    @Column(name = "template_id")
    private UUID templateId;
    @Column(name = "template_name", nullable = false)
    private String templateName;
    @Column(nullable = false)
    private int round = 1;
    @Column(name = "previous_review_id")
    private UUID previousReviewId;
    @Column(nullable = false)
    private String status = "IN_PROGRESS";
    @Column(nullable = false)
    private boolean sample;
    @Column(name = "prepared_by")
    private UUID preparedBy;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "outcome_note", columnDefinition = "TEXT")
    private String outcomeNote;
    @Column(name = "exception_reason", columnDefinition = "TEXT")
    private String exceptionReason;
    @Column(name = "started_by")
    private UUID startedBy;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Version
    private Long version;

    public static ComplianceReview start(TenantId tenantId, UUID clientId, String subjectType, UUID subjectId, String subjectLabel, ComplianceReviewTemplate template,
                                         UUID preparedBy, boolean sample, UUID by) {
        if (template == null) throw new IllegalArgumentException("Choose a checklist");
        if (!template.isActive()) throw new IllegalStateException("That checklist is switched off");
        String type = QualityRules.subjectType(subjectType);
        if (!type.equals(template.getSubjectType())) throw new IllegalArgumentException("That checklist is for " + template.getSubjectType().toLowerCase() + " reviews");
        ComplianceReview r = new ComplianceReview();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.subjectType = type;
        r.subjectId = subjectId;
        r.subjectLabel = QualityRules.text(subjectLabel, "A description of the work", 250, true);
        r.templateId = template.getId();
        r.templateName = template.getName();
        r.preparedBy = preparedBy;
        r.sample = sample;
        r.startedBy = by;
        r.startedAt = Instant.now();
        return r;
    }

    /** The next round of work that was sent back. It starts again from the same checklist, with nothing marked. */
    public ComplianceReview nextRound(UUID by) {
        if (!"REWORK_REQUIRED".equals(status)) throw new IllegalStateException("Only work that was sent back for rework has a next round");
        ComplianceReview r = new ComplianceReview();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.subjectType = subjectType;
        r.subjectId = subjectId;
        r.subjectLabel = subjectLabel;
        r.templateId = templateId;
        r.templateName = templateName;
        r.round = round + 1;
        r.previousReviewId = id;
        r.preparedBy = preparedBy;
        r.sample = sample;
        r.startedBy = by;
        r.startedAt = Instant.now();
        return r;
    }

    public void requireOpen() {
        if (!"IN_PROGRESS".equals(status)) throw new IllegalStateException("This review has been completed and can no longer be changed");
    }

    public void complete(String outcome, UUID reviewer, String note, String exceptionReason, List<QualityRules.Line> lines) {
        requireOpen();
        String o = outcome == null ? "" : outcome.trim().toUpperCase();
        String problem = QualityRules.completionProblem(o, lines);
        if (problem != null) throw new IllegalStateException(problem);
        String n = QualityRules.outcomeNote(note);
        String exception = QualityRules.exception(preparedBy, reviewer, exceptionReason);
        this.status = o;
        this.reviewedBy = reviewer;
        this.outcomeNote = n;
        this.exceptionReason = exception;
        this.completedAt = Instant.now();
    }
}
