package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A policy, procedure or standard. Its content lives in numbered {@link PolicyVersion}s; this record says which one is in force and when it is next due for review. */
@Entity
@Table(name = "legalcompliance_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy extends AggregateRoot<Policy> {

    public static final int MIN_REASON = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "policy_type", nullable = false, length = 10) private PolicyType policyType;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "review_months", nullable = false) private int reviewMonths = 12;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private PolicyStatus status = PolicyStatus.ACTIVE;
    @Column(name = "published_version_id") private UUID publishedVersionId;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;
    @Column(name = "retired_reason", columnDefinition = "TEXT") private String retiredReason;
    @Column(name = "retired_at") private Instant retiredAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static Policy create(TenantId tenantId, String reference, String title, PolicyType type, UUID ownerId, String ownerName, int reviewMonths, UUID by, String byName) {
        Policy p = new Policy();
        p.initTenantId(tenantId);
        p.reference = reference;
        p.createdBy = by;
        p.createdByName = byName;
        p.apply(title, type, ownerId, ownerName, reviewMonths);
        return p;
    }

    public void update(String title, PolicyType type, UUID ownerId, String ownerName, int reviewMonths) {
        requireActive();
        apply(title, type, ownerId, ownerName, reviewMonths);
    }

    private void apply(String title, PolicyType type, UUID ownerId, String ownerName, int reviewMonths) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (type == null) throw new IllegalArgumentException("A type is required");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (reviewMonths < 1 || reviewMonths > 60) throw new IllegalArgumentException("The review period must be between 1 and 60 months");
        this.title = title.trim();
        this.policyType = type;
        this.ownerId = ownerId;
        this.ownerName = ownerName.trim();
        this.reviewMonths = reviewMonths;
    }

    /** Called when a version is published: it becomes the one in force and the review clock restarts. */
    public void markPublished(UUID versionId, LocalDate publishedOn) {
        requireActive();
        this.publishedVersionId = versionId;
        this.nextReviewDue = publishedOn.plusMonths(reviewMonths);
    }

    public void retire(String reason) {
        requireActive();
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("A reason of at least " + MIN_REASON + " characters is required");
        this.retiredReason = reason.trim();
        this.retiredAt = Instant.now();
        this.status = PolicyStatus.RETIRED;
    }

    public boolean isActive() { return status == PolicyStatus.ACTIVE; }
    private void requireActive() { if (status != PolicyStatus.ACTIVE) throw new IllegalStateException("The policy is retired"); }
}
