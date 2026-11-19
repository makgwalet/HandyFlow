package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A continuity plan for one process. DRAFT → APPROVED (by someone other than the author) → RETIRED. Only DRAFT plans are edited; changing an approved plan means a new draft,
 * and approving it retires the old one, so the approved text is never altered in place. One approved plan per process (also a unique index).
 */
@Entity
@Table(name = "legalcompliance_bc_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BcPlan extends AggregateRoot<BcPlan> {

    public static final int MIN_CONTENT = 50, MIN_REASON = 10;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "process_id", nullable = false) private UUID processId;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "version_label", nullable = false, length = 20) private String versionLabel;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private BcPlanStatus status = BcPlanStatus.DRAFT;
    @Column(name = "review_months", nullable = false) private int reviewMonths = 12;
    @Column(name = "author_user_id", nullable = false) private UUID authorUserId;
    @Column(name = "author_name", nullable = false, length = 255) private String authorName;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_by_name", length = 255) private String approvedByName;
    @Column(name = "approved_on") private LocalDate approvedOn;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;
    @Column(name = "last_reviewed_on") private LocalDate lastReviewedOn;
    @Column(name = "retired_reason", columnDefinition = "TEXT") private String retiredReason;

    public static BcPlan draft(TenantId t, String reference, UUID processId, String title, String versionLabel, String owner, String content, int reviewMonths, UUID authorId, String authorName) {
        if (authorId == null || authorName == null || authorName.isBlank()) throw new IllegalArgumentException("The author is required");
        BcPlan p = new BcPlan();
        p.initTenantId(t);
        p.reference = reference;
        p.processId = processId;
        p.authorUserId = authorId;
        p.authorName = authorName;
        p.apply(title, versionLabel, owner, content, reviewMonths);
        return p;
    }

    public void update(String title, String versionLabel, String owner, String content, int reviewMonths) {
        if (status != BcPlanStatus.DRAFT) throw new IllegalStateException("Only a draft can be edited. Write a new draft to change an approved plan");
        apply(title, versionLabel, owner, content, reviewMonths);
    }

    private void apply(String title, String versionLabel, String owner, String content, int reviewMonths) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (versionLabel == null || versionLabel.isBlank()) throw new IllegalArgumentException("A version label is required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (content == null || content.trim().length() < MIN_CONTENT) throw new IllegalArgumentException("The plan needs at least " + MIN_CONTENT + " characters of content");
        if (reviewMonths < 1 || reviewMonths > 36) throw new IllegalArgumentException("The review period must be between 1 and 36 months");
        this.title = title.trim();
        this.versionLabel = versionLabel.trim();
        this.ownerName = owner.trim();
        this.content = content.trim();
        this.reviewMonths = reviewMonths;
    }

    /** Segregation of duties: the approver cannot be the author. */
    public void approve(UUID approverId, String approverName, LocalDate today) {
        if (status != BcPlanStatus.DRAFT) throw new IllegalStateException("Only a draft can be approved");
        if (approverId == null || approverName == null || approverName.isBlank()) throw new IllegalArgumentException("The approver is required");
        if (approverId.equals(authorUserId)) throw new IllegalStateException("A plan cannot be approved by its author");
        this.status = BcPlanStatus.APPROVED;
        this.approvedByUserId = approverId;
        this.approvedByName = approverName;
        this.approvedOn = today;
        this.lastReviewedOn = today;
        this.nextReviewDue = today.plusMonths(reviewMonths);
    }

    /** Confirms an approved plan is still right, without changing it. Moves the next review date on. */
    public void confirmReview(LocalDate today) {
        if (status != BcPlanStatus.APPROVED) throw new IllegalStateException("Only an approved plan can be reviewed");
        this.lastReviewedOn = today;
        this.nextReviewDue = today.plusMonths(reviewMonths);
    }

    public void retire(String reason) {
        if (status == BcPlanStatus.RETIRED) throw new IllegalStateException("This plan is already retired");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("A reason of at least " + MIN_REASON + " characters is required");
        this.status = BcPlanStatus.RETIRED;
        this.retiredReason = reason.trim();
    }
}
