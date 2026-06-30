// security/domain/model/PatrolRoute.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PatrolRoute — a named, ordered set of checkpoints with an interval config.
 *
 * Drives PatrolRoundService.generateRoundsForShift(): when a shift starts,
 * every active route for the site generates the expected number of rounds
 * for the shift's duration. e.g. a 12-hour shift with a 120-minute interval
 * route produces 6 expected rounds.
 *
 * WHY interval_minutes + tolerance_minutes rather than fixed clock times?
 * "Every 2 hours" is more robust to shift-start-time variation than
 * "at 08:00, 10:00, 12:00..." — a guard starting 20 minutes late doesn't
 * throw off the whole day's schedule, the rounds shift with them.
 */
@Entity
@Table(name = "security_patrol_routes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PatrolRoute {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes = 120;

    @Column(name = "tolerance_minutes", nullable = false)
    private int toleranceMinutes = 20;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    @OrderBy("sequence ASC")
    private List<PatrolRouteCheckpoint> checkpoints = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static PatrolRoute create(TenantId tenantId, UUID siteId, String name,
                                     int intervalMinutes, int toleranceMinutes) {
        PatrolRoute r       = new PatrolRoute();
        r.tenantId          = tenantId;
        r.siteId            = siteId;
        r.name              = name.strip();
        r.intervalMinutes   = intervalMinutes;
        r.toleranceMinutes  = toleranceMinutes;
        r.active            = true;
        r.createdAt         = Instant.now();
        r.updatedAt         = Instant.now();
        return r;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    // ── Computation ────────────────────────────────────────────────────────────

    /**
     * How many rounds are expected over a shift of the given duration.
     * e.g. 720 minutes (12h) / 120-minute interval = 6 rounds.
     */
    public int expectedRoundsForShift(long shiftMinutes) {
        if (intervalMinutes <= 0) return 0;
        return (int) (shiftMinutes / intervalMinutes);
    }
}
