package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One stay of a tyre at one position on one vehicle. Open while {@code removedAt} is null. */
@Entity
@Table(name = "fleet_tyre_fitments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TyreFitment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tyre_id", nullable = false)
    private UUID tyreId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 10)
    private String position;

    @Column(name = "fitted_at", nullable = false)
    private Instant fittedAt;

    @Column(name = "fitted_odometer", nullable = false)
    private Integer fittedOdometer;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_odometer")
    private Integer removedOdometer;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", length = 30)
    private TyreRemovalReason removalReason;

    @Column(name = "fit_work_order_id")
    private UUID fitWorkOrderId;

    @Column(name = "remove_work_order_id")
    private UUID removeWorkOrderId;

    public static TyreFitment open(TenantId tenantId, UUID tyreId, UUID vehicleId, String position,
                                   Instant fittedAt, int fittedOdometer, UUID fitWorkOrderId) {
        TyreFitment f = new TyreFitment();
        f.tenantId = tenantId;
        f.tyreId = tyreId;
        f.vehicleId = vehicleId;
        f.position = position;
        f.fittedAt = fittedAt;
        f.fittedOdometer = fittedOdometer;
        f.fitWorkOrderId = fitWorkOrderId;
        return f;
    }

    public void close(Instant at, int odometer, TyreRemovalReason reason, UUID removeWorkOrderId) {
        if (removedAt != null) throw new IllegalStateException("This fitment is already closed");
        if (odometer < fittedOdometer) {
            throw new IllegalArgumentException("Removal odometer cannot be lower than the fitting odometer");
        }
        this.removedAt = at;
        this.removedOdometer = odometer;
        this.removalReason = reason;
        this.removeWorkOrderId = removeWorkOrderId;
    }

    /** Distance run on this fitment; null while it is still open. */
    public Integer getKm() {
        return removedOdometer != null ? removedOdometer - fittedOdometer : null;
    }
}