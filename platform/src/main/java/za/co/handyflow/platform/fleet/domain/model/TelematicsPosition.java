package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One GPS fix. Read-only from JPA's point of view: rows are written by the ingest service with a
 * batched {@code INSERT ... ON CONFLICT DO NOTHING} (volume, and duplicate fixes from a provider
 * that re-sends are silently ignored), and only ever read through this mapping.
 */
@Entity
@Table(name = "fleet_telematics_positions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TelematicsPosition {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false, insertable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private double latitude;

    @Column(nullable = false, insertable = false, updatable = false)
    private double longitude;

    @Column(name = "speed_kph", insertable = false, updatable = false)
    private Double speedKph;

    @Column(insertable = false, updatable = false)
    private Integer heading;

    @Column(name = "odometer_km", insertable = false, updatable = false)
    private Double odometerKm;

    @Column(name = "ignition_on", insertable = false, updatable = false)
    private Boolean ignitionOn;
}