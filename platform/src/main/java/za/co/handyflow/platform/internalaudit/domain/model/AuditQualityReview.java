package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A review of an engagement by someone who is not on its team. REQUESTED -> IN_PROGRESS (first answer) -> COMPLETED with a conclusion, or CANCELLED.
 * A review with any "No" answer cannot conclude SATISFACTORY. The reviewer's answers are {@link AuditQualityCheck}.
 */
@Entity(name = "AuditQualityReview")
@Table(name = "audit_quality_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditQualityReview {
    public static final int MIN_TEXT = 10;
    public static final Set<String> CONCLUSIONS = Set.of("SATISFACTORY", "NEEDS_IMPROVEMENT", "UNSATISFACTORY");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "reviewer_id", nullable = false) private UUID reviewerId;
    @Column(name = "reviewer_name", nullable = false) private String reviewerName;
    @Column(name = "requested_by", nullable = false) private UUID requestedBy;
    @Column(name = "requested_by_name", nullable = false) private String requestedByName;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt = Instant.now();
    @Column(name = "status", nullable = false) private String status = "REQUESTED";
    @Column(name = "conclusion") private String conclusion;
    @Column(name = "conclusion_note", columnDefinition = "TEXT") private String conclusionNote;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "cancelled_at") private Instant cancelledAt;

    private static String text(String t, String what) {
        if (t == null || t.trim().length() < MIN_TEXT) throw new IllegalArgumentException(what + " needs at least " + MIN_TEXT + " characters");
        return t.trim();
    }

    public static AuditQualityReview request(UUID tenantId, UUID engagementId, UUID reviewerId, String reviewerName, UUID by, String byName) {
        if (reviewerId == null) throw new IllegalArgumentException("Choose a reviewer");
        AuditQualityReview r = new AuditQualityReview();
        r.tenantId = tenantId; r.engagementId = engagementId; r.reviewerId = reviewerId; r.reviewerName = reviewerName; r.requestedBy = by; r.requestedByName = byName;
        return r;
    }

    public boolean isLive() { return "REQUESTED".equals(status) || "IN_PROGRESS".equals(status); }

    public void requireReviewer(UUID user) {
        if (!reviewerId.equals(user)) throw new IllegalStateException("Only the appointed reviewer, " + reviewerName + ", can answer this review");
    }

    public void requireLive() {
        if (!isLive()) throw new IllegalStateException("This review is " + status.toLowerCase() + " and can no longer be changed");
    }

    public void answered() {
        requireLive();
        if ("REQUESTED".equals(status)) this.status = "IN_PROGRESS";
    }

    /** @param unanswered checklist items still without an answer; @param noAnswers items answered "No". */
    public void complete(UUID by, String conclusion, String note, int unanswered, int noAnswers) {
        requireLive();
        requireReviewer(by);
        if (unanswered > 0) throw new IllegalStateException(unanswered + " checklist item" + (unanswered == 1 ? " is" : "s are") + " still unanswered");
        String c = conclusion == null ? "" : conclusion.toUpperCase();
        if (!CONCLUSIONS.contains(c)) throw new IllegalArgumentException("Conclusion must be SATISFACTORY, NEEDS_IMPROVEMENT or UNSATISFACTORY");
        if (noAnswers > 0 && "SATISFACTORY".equals(c)) throw new IllegalStateException("A review with " + noAnswers + " 'No' answer" + (noAnswers == 1 ? "" : "s") + " cannot conclude satisfactory");
        this.conclusionNote = text(note, "The conclusion note");
        this.conclusion = c; this.status = "COMPLETED"; this.completedAt = Instant.now();
    }

    public void cancel(String reason) {
        requireLive();
        this.cancelReason = text(reason, "The reason"); this.status = "CANCELLED"; this.cancelledAt = Instant.now();
    }
}
