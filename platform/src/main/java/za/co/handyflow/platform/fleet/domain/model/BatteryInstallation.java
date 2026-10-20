package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One stay of a battery on one vehicle. Open while {@code removedAt} is null. */
@Entity
@Table(name = "fleet_battery_installations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BatteryInstallation {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "battery_id", nullable = false)
    private UUID batteryId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 10)
    private String position;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "installed_odometer", nullable = false)
    private Integer installedOdometer;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_odometer")
    private Integer removedOdometer;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", length = 30)
    private BatteryRemovalReason removalReason;

    @Column(name = "install_work_order_id")
    private UUID installWorkOrderId;

    @Column(name = "remove_work_order_id")
    private UUID removeWorkOrderId;

    public static BatteryInstallation open(TenantId tenantId, UUID batteryId, UUID vehicleId, String position,
                                           Instant installedAt, int installedOdometer, UUID installWorkOrderId) {
        BatteryInstallation i = new BatteryInstallation();
        i.tenantId = tenantId;
        i.batteryId = batteryId;
        i.vehicleId = vehicleId;
        i.position = position;
        i.installedAt = installedAt;
        i.installedOdometer = installedOdometer;
        i.installWorkOrderId = installWorkOrderId;
        return i;
    }

    public void close(Instant at, int odometer, BatteryRemovalReason reason, UUID removeWorkOrderId) {
        if (removedAt != null) throw new IllegalStateException("This installation is already closed");
        this.removedAt = at;
        this.removedOdometer = odometer;
        this.removalReason = reason;
        this.removeWorkOrderId = removeWorkOrderId;
    }

    public Integer getKm() {
        return removedOdometer != null ? removedOdometer - installedOdometer : null;
    }
}