package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** A periodic review of a client. Append-only. When the client's summary was readable at the time, the raw counts seen are kept so the review can be re-read as it was. */
@Entity
@Table(name = "legalcompliance_provider_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderReview extends AggregateRoot<ProviderReview> {

    public static final int MIN_NOTES = 15;

    @Column(name = "provider_client_id", nullable = false) private UUID providerClientId;
    @Column(name = "review_date", nullable = false) private LocalDate reviewDate;
    @Column(name = "reviewer_user_id", nullable = false) private UUID reviewerUserId;
    @Column(name = "reviewer_name", nullable = false, length = 255) private String reviewerName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ReviewOutcome outcome;
    @Column(nullable = false, columnDefinition = "TEXT") private String notes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Long> metrics;
    @Column(name = "next_review_due", nullable = false) private LocalDate nextReviewDue;

    public static ProviderReview record(TenantId t, UUID clientId, LocalDate reviewDate, UUID reviewerId, String reviewerName, ReviewOutcome outcome, String notes,
                                        Map<String, Long> metrics, LocalDate nextDue, LocalDate today) {
        if (reviewDate == null) throw new IllegalArgumentException("The review date is required");
        if (reviewDate.isAfter(today)) throw new IllegalArgumentException("A review cannot be dated in the future");
        if (outcome == null) throw new IllegalArgumentException("An outcome is required");
        if (notes == null || notes.trim().length() < MIN_NOTES) throw new IllegalArgumentException("Review notes of at least " + MIN_NOTES + " characters are required");
        if (reviewerId == null || reviewerName == null || reviewerName.isBlank()) throw new IllegalArgumentException("The reviewer is required");
        ProviderReview r = new ProviderReview();
        r.initTenantId(t);
        r.providerClientId = clientId;
        r.reviewDate = reviewDate;
        r.reviewerUserId = reviewerId;
        r.reviewerName = reviewerName;
        r.outcome = outcome;
        r.notes = notes.trim();
        r.metrics = metrics;
        r.nextReviewDue = nextDue;
        return r;
    }
}
