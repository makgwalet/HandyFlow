package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Whether a vehicle was inside a geofence at its last evaluated position, so the next position can tell if it crossed the boundary. */
@Entity
@Table(name = "fleet_geofence_states")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GeofenceState {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "geofence_id", nullable = false)
    private UUID geofenceId;

    @Column(nullable = false)
    private boolean inside;

    @Column(nullable = false)
    private Instant since;

    public static GeofenceState first(UUID vehicleId, UUID geofenceId, boolean inside, Instant at) {
        GeofenceState s = new GeofenceState();
        s.vehicleId = vehicleId;
        s.geofenceId = geofenceId;
        s.inside = inside;
        s.since = at;
        return s;
    }

    public void moveTo(boolean inside, Instant at) {
        this.inside = inside;
        this.since = at;
    }
}