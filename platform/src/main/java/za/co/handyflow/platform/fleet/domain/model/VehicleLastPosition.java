package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A vehicle's latest known state — what the live map shows — plus the running state the
 * rule engine carries between ingest batches (when the current idle spell began, whether it has
 * already been reported, when speeding was last reported). One row per vehicle.
 */
@Entity
@Table(name = "fleet_vehicle_last_positions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleLastPosition {

    @Id
    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "speed_kph")
    private Double speedKph;

    private Integer heading;

    @Column(name = "odometer_km")
    private Double odometerKm;

    @Column(name = "ignition_on")
    private Boolean ignitionOn;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "idle_since")
    private Instant idleSince;

    @Column(name = "idle_event_raised", nullable = false)
    private boolean idleEventRaised = false;

    @Column(name = "last_speeding_event_at")
    private Instant lastSpeedingEventAt;

    public static VehicleLastPosition first(TenantId tenantId, UUID vehicleId, double lat, double lng, Double speed,
                                            Integer heading, Double odometerKm, Boolean ignitionOn, Instant recordedAt) {
        VehicleLastPosition p = new VehicleLastPosition();
        p.tenantId = tenantId;
        p.vehicleId = vehicleId;
        p.update(lat, lng, speed, heading, odometerKm, ignitionOn, recordedAt);
        return p;
    }

    public void update(double lat, double lng, Double speed, Integer heading, Double odometerKm,
                       Boolean ignitionOn, Instant recordedAt) {
        this.latitude = lat;
        this.longitude = lng;
        this.speedKph = speed;
        this.heading = heading;
        if (odometerKm != null) this.odometerKm = odometerKm;
        this.ignitionOn = ignitionOn;
        this.recordedAt = recordedAt;
        this.receivedAt = Instant.now();
    }

    /**
     * Idle = ignition on and (almost) stationary. Returns the idle duration to report once
     * it reaches {@code threshold} (and only once per spell), otherwise null. Unknown ignition or
     * speed is treated as "not idle": no ignition feed, no idling alerts.
     */
    public Duration trackIdle(Boolean ignitionOn, Double speedKph, Instant at, Duration threshold) {
        boolean idle = Boolean.TRUE.equals(ignitionOn) && speedKph != null && speedKph < 2.0;
        if (!idle) {
            this.idleSince = null;
            this.idleEventRaised = false;
            return null;
        }
        if (idleSince == null) {
            this.idleSince = at;
            this.idleEventRaised = false;
            return null;
        }
        Duration idleFor = Duration.between(idleSince, at);
        if (!idleEventRaised && idleFor.compareTo(threshold) >= 0) {
            this.idleEventRaised = true;
            return idleFor;
        }
        return null;
    }

    public void markSpeedingReported(Instant at) {
        this.lastSpeedingEventAt = at;
    }
}