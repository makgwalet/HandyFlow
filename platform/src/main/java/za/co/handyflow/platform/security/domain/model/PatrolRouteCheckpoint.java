// security/domain/model/PatrolRouteCheckpoint.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * PatrolRouteCheckpoint — one checkpoint's position within a patrol route.
 *
 * Owned by PatrolRoute (cascade persist/remove) — there is no standalone
 * repository for this entity; it's always accessed via route.getCheckpoints().
 *
 * expectedMinutesAfterRouteStart: optional per-checkpoint dwell offset within
 * the round (e.g. "Parking Lot" checkpoint should be reached ~15 minutes into
 * the round). Null = no per-checkpoint timing expectation, only the route-level
 * interval/tolerance applies.
 */
@Entity
@Table(name = "security_patrol_route_checkpoints")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PatrolRouteCheckpoint {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "route_id", nullable = false, insertable = false, updatable = false)
    private UUID routeId;

    @Column(name = "checkpoint_id", nullable = false)
    private UUID checkpointId;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "expected_minutes_after_route_start")
    private Integer expectedMinutesAfterRouteStart;

    public static PatrolRouteCheckpoint create(UUID routeId, UUID checkpointId, int sequence) {
        PatrolRouteCheckpoint c = new PatrolRouteCheckpoint();
        c.routeId      = routeId;
        c.checkpointId = checkpointId;
        c.sequence     = sequence;
        return c;
    }
}
