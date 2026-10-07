// security/domain/model/GuardRating.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A rating of a guard, 1 to 5 across six dimensions, from a client or a supervisor. Ratings are never edited or deleted. */
@Entity
@Table(name = "security_guard_ratings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardRating {

    public enum Source { CLIENT, SUPERVISOR }

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "rater_name", length = 200)
    private String raterName;

    @Column(name = "rated_on", nullable = false)
    private LocalDate ratedOn;

    @Column(nullable = false) private int punctuality;
    @Column(nullable = false) private int professionalism;
    @Column(nullable = false) private int appearance;
    @Column(nullable = false) private int communication;
    @Column(nullable = false) private int alertness;
    @Column(name = "incident_handling", nullable = false) private int incidentHandling;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static GuardRating create(TenantId tenantId, UUID guardId, UUID siteId, Source source, String raterName, LocalDate ratedOn,
                                     int[] scores, String comment, UUID by, String byName) {
        GuardRating r = new GuardRating();
        r.tenantId = tenantId; r.guardId = guardId; r.siteId = siteId; r.source = source;
        r.raterName = blank(raterName); r.ratedOn = ratedOn;
        r.punctuality = scores[0]; r.professionalism = scores[1]; r.appearance = scores[2];
        r.communication = scores[3]; r.alertness = scores[4]; r.incidentHandling = scores[5];
        r.comment = blank(comment); r.createdBy = by; r.createdByName = byName;
        return r;
    }

    /** Average of the six dimensions. */
    public double average() {
        return (punctuality + professionalism + appearance + communication + alertness + incidentHandling) / 6.0;
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
