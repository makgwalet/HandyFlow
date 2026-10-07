// security/domain/model/GuardReview.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A supervisor's review of a guard over a period. A record of what was said on a date: never edited or deleted. */
@Entity
@Table(name = "security_guard_reviews")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardReview {

    public enum Overall { EXCEEDS, MEETS, BELOW }

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "reviewer_name", nullable = false, length = 200)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Overall overall;

    private int punctuality;
    private int professionalism;
    private int appearance;
    private int communication;
    private int alertness;

    @Column(name = "incident_handling")
    private int incidentHandling;

    @Column(columnDefinition = "text")
    private String strengths;

    @Column(columnDefinition = "text")
    private String improvements;

    @Column(name = "training_needs", columnDefinition = "text")
    private String trainingNeeds;

    @Column(name = "actions_agreed", columnDefinition = "text")
    private String actionsAgreed;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "rating_id")
    private UUID ratingId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static GuardReview create(TenantId tenantId, UUID guardId, UUID siteId, LocalDate reviewDate, LocalDate from, LocalDate to,
                                     String reviewerName, Overall overall, int[] scores, String strengths, String improvements,
                                     String trainingNeeds, String actionsAgreed, LocalDate followUp, UUID ratingId, UUID by, String byName) {
        GuardReview r = new GuardReview();
        r.tenantId = tenantId; r.guardId = guardId; r.siteId = siteId; r.reviewDate = reviewDate; r.periodFrom = from; r.periodTo = to;
        r.reviewerName = reviewerName; r.overall = overall;
        r.punctuality = scores[0]; r.professionalism = scores[1]; r.appearance = scores[2];
        r.communication = scores[3]; r.alertness = scores[4]; r.incidentHandling = scores[5];
        r.strengths = blank(strengths); r.improvements = blank(improvements); r.trainingNeeds = blank(trainingNeeds);
        r.actionsAgreed = blank(actionsAgreed); r.followUpDate = followUp; r.ratingId = ratingId;
        r.createdBy = by; r.createdByName = byName;
        return r;
    }

    public double average() { return (punctuality + professionalism + appearance + communication + alertness + incidentHandling) / 6.0; }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
