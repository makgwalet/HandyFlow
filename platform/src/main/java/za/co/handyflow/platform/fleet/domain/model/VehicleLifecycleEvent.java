package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One entry in a vehicle's lifecycle audit trail. Immutable once written. */
@Entity
@Table(name = "fleet_vehicle_lifecycle_events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleLifecycleEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private LifecycleEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private VehicleLifecycleStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private VehicleLifecycleStatus toStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    public static VehicleLifecycleEvent record(TenantId tenantId, UUID vehicleId, LifecycleEventType type,
                                               VehicleLifecycleStatus from, VehicleLifecycleStatus to,
                                               Instant occurredAt, String reason, String note, UUID recordedBy) {
        VehicleLifecycleEvent e = new VehicleLifecycleEvent();
        e.tenantId = tenantId;
        e.vehicleId = vehicleId;
        e.eventType = type;
        e.fromStatus = from;
        e.toStatus = to;
        e.occurredAt = occurredAt;
        e.recordedAt = Instant.now();
        e.reason = reason;
        e.note = note;
        e.recordedBy = recordedBy;
        return e;
    }
}