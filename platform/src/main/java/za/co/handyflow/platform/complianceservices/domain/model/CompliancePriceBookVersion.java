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
 * One numbered version of the practice's price book. It is a draft until someone else approves it. After that it is
 * never edited: a change is a new version, and a quote keeps the version it was priced on.
 */
@Entity
@Table(name = "compliance_price_book_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompliancePriceBookVersion {

    public static final String DRAFT = "DRAFT";
    public static final String IN_REVIEW = "IN_REVIEW";
    public static final String APPROVED = "APPROVED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus = DRAFT;

    @Column(nullable = false)
    private String currency = "ZAR";

    private String label;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "authored_by")
    private UUID authoredBy;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "review_exception_reason", columnDefinition = "TEXT")
    private String reviewExceptionReason;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    private Long version;

    public static CompliancePriceBookVersion draft(TenantId tenantId, int number, UUID author) {
        CompliancePriceBookVersion v = new CompliancePriceBookVersion();
        v.tenantId = tenantId;
        v.versionNumber = number;
        v.authoredBy = author;
        v.createdAt = Instant.now();
        v.updatedAt = Instant.now();
        return v;
    }

    public boolean isDraft() { return DRAFT.equals(reviewStatus); }
    public boolean isApproved() { return APPROVED.equals(reviewStatus); }

    public void edit(String label, String changeSummary) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be edited. Start a new version to change an approved one.");
        this.label = ClientProfileRules.clean(label, 120);
        this.changeSummary = ClientProfileRules.clean(changeSummary, 2000);
        this.updatedAt = Instant.now();
    }

    public void submit(UUID by, int lines, int incompleteLines) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be sent for review");
        if (lines < 1) throw new IllegalArgumentException("Before review it needs at least one priced service");
        if (incompleteLines > 0) throw new IllegalArgumentException("Before review " + incompleteLines + " line(s) need the amounts their pricing model uses");
        if (versionNumber > 1 && (changeSummary == null || changeSummary.isBlank())) {
            throw new IllegalArgumentException("Before review it needs a summary of what changed");
        }
        this.reviewStatus = IN_REVIEW;
        this.submittedBy = by;
        this.submittedAt = Instant.now();
        this.reviewNote = null;
        this.updatedAt = Instant.now();
    }

    /** The reviewer is not the person who wrote or sent it, unless they record why no one else can. */
    public void approve(UUID reviewer, LocalDate from, String exceptionReason, String note) {
        if (!IN_REVIEW.equals(reviewStatus)) throw new IllegalStateException("Only a version in review can be approved");
        if (reviewer == null) throw new IllegalArgumentException("A reviewer is required");
        if (from == null) throw new IllegalArgumentException("Say the date this version takes effect");
        String reason = null;
        if (reviewer.equals(authoredBy) || reviewer.equals(submittedBy)) {
            String r = exceptionReason == null ? "" : exceptionReason.trim();
            if (r.length() < ObligationRules.MIN_EXCEPTION_REASON) {
                throw new IllegalArgumentException("You wrote or sent this, so someone else should approve it. If no one else can, give a reason of at least "
                        + ObligationRules.MIN_EXCEPTION_REASON + " characters");
            }
            reason = r.length() > 1000 ? r.substring(0, 1000) : r;
        }
        this.reviewStatus = APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = ClientProfileRules.clean(note, 2000);
        this.reviewExceptionReason = reason;
        this.effectiveFrom = from;
        this.updatedAt = Instant.now();
    }

    public void reject(UUID reviewer, String reason) {
        if (!IN_REVIEW.equals(reviewStatus)) throw new IllegalStateException("Only a version in review can be sent back");
        String r = reason == null ? "" : reason.trim();
        if (r.length() < ObligationRules.MIN_REVIEW_REASON) throw new IllegalArgumentException("Say what needs to change (at least " + ObligationRules.MIN_REVIEW_REASON + " characters)");
        this.reviewStatus = DRAFT;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = r.length() > 2000 ? r.substring(0, 2000) : r;
        this.updatedAt = Instant.now();
    }

    /** Ends an approved version. Its prices are untouched. */
    public void endOn(LocalDate date) {
        if (!isApproved()) throw new IllegalStateException("Only an approved version can end");
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) throw new IllegalArgumentException("It cannot end before it starts");
        this.effectiveTo = date;
        this.updatedAt = Instant.now();
    }

    public ObligationRules.Span span() { return new ObligationRules.Span(versionNumber, isApproved(), effectiveFrom, effectiveTo); }
}
