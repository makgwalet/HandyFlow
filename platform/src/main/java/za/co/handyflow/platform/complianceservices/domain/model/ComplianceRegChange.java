package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One change in the rules. Flagged (by a person, or later by automation), then reviewed. Confirming records the reviewer, their note
 * and the effective date; the same person who raised it needs a recorded reason to review it. Nothing is deleted.
 */
@Entity(name = "ComplianceRegChange")
@Table(name = "compliance_reg_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceRegChange {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(name = "change_type", nullable = false)
    private String changeType;
    @Column(name = "publication_date")
    private LocalDate publicationDate;
    @Column(name = "effective_date")
    private LocalDate effectiveDate;
    @Column(name = "reference_url")
    private String referenceUrl;
    @Column(nullable = false)
    private String origin = "MANUAL";
    @Column(nullable = false)
    private String status = "DETECTED";
    @Column(name = "raised_by")
    private UUID raisedBy;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Column(name = "review_started_by")
    private UUID reviewStartedBy;
    @Column(name = "review_started_at")
    private Instant reviewStartedAt;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;
    @Column(name = "review_exception_reason", columnDefinition = "TEXT")
    private String reviewExceptionReason;
    @Column(name = "impacts_raised_at")
    private Instant impactsRaisedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static ComplianceRegChange raise(TenantId tenantId, UUID sourceId, String title, String summary, String changeType, LocalDate publicationDate,
                                            LocalDate effectiveDate, String referenceUrl, UUID by, LocalDate today) {
        ComplianceRegChange c = new ComplianceRegChange();
        c.tenantId = tenantId;
        c.sourceId = sourceId;
        c.raisedBy = by;
        c.raisedAt = Instant.now();
        c.fill(title, summary, changeType, publicationDate, effectiveDate, referenceUrl, today);
        return c;
    }

    private void fill(String title, String summary, String changeType, LocalDate publicationDate, LocalDate effectiveDate, String referenceUrl, LocalDate today) {
        this.title = RegulatoryRules.text(title, "Title", 250, true);
        this.summary = RegulatoryRules.text(summary, "Summary", 4000, false);
        this.changeType = RegulatoryRules.changeType(changeType);
        RegulatoryRules.checkDates(publicationDate, effectiveDate, today);
        this.publicationDate = publicationDate;
        this.effectiveDate = effectiveDate;
        this.referenceUrl = RegulatoryRules.url(referenceUrl);
        this.updatedAt = Instant.now();
    }

    public void edit(UUID sourceId, String title, String summary, String changeType, LocalDate publicationDate, LocalDate effectiveDate, String referenceUrl, LocalDate today) {
        if (!RegulatoryRules.canEdit(status)) throw new IllegalStateException("A change that has been confirmed or dismissed can no longer be edited");
        this.sourceId = sourceId;
        fill(title, summary, changeType, publicationDate, effectiveDate, referenceUrl, today);
    }

    public void startReview(UUID by) {
        if (!"DETECTED".equals(status)) throw new IllegalStateException("Only a newly flagged change can be taken into review");
        if (by == null) throw new IllegalArgumentException("A person is required");
        this.status = "IN_REVIEW";
        this.reviewStartedBy = by;
        this.reviewStartedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void confirm(UUID reviewer, String note, String exceptionReason) {
        if (!"IN_REVIEW".equals(status)) throw new IllegalStateException("Take the change into review before confirming it");
        String problem = RegulatoryRules.confirmProblem(changeType, effectiveDate);
        if (problem != null) throw new IllegalStateException(problem);
        decide("CONFIRMED", reviewer, note, exceptionReason);
    }

    public void dismiss(UUID reviewer, String note, String exceptionReason) {
        if (!RegulatoryRules.canEdit(status)) throw new IllegalStateException("This change has already been decided");
        decide("DISMISSED", reviewer, note, exceptionReason);
    }

    private void decide(String to, UUID reviewer, String note, String exceptionReason) {
        String n = RegulatoryRules.reviewNote(note);
        String exception = RegulatoryRules.exception(raisedBy, reviewer, exceptionReason);
        this.status = to;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = n;
        this.reviewExceptionReason = exception;
        this.updatedAt = Instant.now();
    }

    public void impactsRaised() { this.impactsRaisedAt = Instant.now(); }
}
