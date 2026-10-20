package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Something notable a vehicle did. {@code driverId} is the driver resolved at the time it
 * happened (the vehicle's trip at that moment, else its assigned driver) — that attribution is what the
 * driver Safety score is built from, so it is fixed when the event is recorded.
 */
@Entity
@Table(name = "fleet_telematics_events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TelematicsEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private TelematicsEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TelematicsSeverity severity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private Double latitude;
    private Double longitude;

    @Column(name = "event_value")
    private Double value;

    @Column(name = "limit_value")
    private Double limitValue;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(length = 500)
    private String details;

    @Column(name = "geofence_id")
    private UUID geofenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TelematicsEventSource source;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TelematicsEvent record(TenantId tenantId, UUID vehicleId, UUID driverId, UUID tripId,
                                         TelematicsEventType type, TelematicsSeverity severity, Instant occurredAt,
                                         Double latitude, Double longitude, Double value, Double limitValue,
                                         Integer durationSeconds, String details, UUID geofenceId,
                                         TelematicsEventSource source) {
        TelematicsEvent e = new TelematicsEvent();
        e.tenantId = tenantId;
        e.vehicleId = vehicleId;
        e.driverId = driverId;
        e.tripId = tripId;
        e.type = type;
        e.severity = severity;
        e.occurredAt = occurredAt;
        e.latitude = latitude;
        e.longitude = longitude;
        e.value = value;
        e.limitValue = limitValue;
        e.durationSeconds = durationSeconds;
        e.details = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        e.geofenceId = geofenceId;
        e.source = source;
        e.createdAt = Instant.now();
        return e;
    }

    public void acknowledge(UUID by) {
        if (acknowledgedAt == null) {
            this.acknowledgedAt = Instant.now();
            this.acknowledgedBy = by;
        }
    }
}