package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tread-depth (and optionally pressure) reading. Immutable once recorded. */
@Entity
@Table(name = "fleet_tyre_measurements")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TyreMeasurement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tyre_id", nullable = false)
    private UUID tyreId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "tread_depth_mm", nullable = false, precision = 4, scale = 1)
    private BigDecimal treadDepthMm;

    @Column(name = "pressure_kpa")
    private Integer pressureKpa;

    private Integer odometer;

    @Column(length = 500)
    private String notes;

    @Column(name = "measured_by")
    private UUID measuredBy;

    public static TyreMeasurement record(TenantId tenantId, UUID tyreId, UUID vehicleId, Instant measuredAt,
                                         BigDecimal treadDepthMm, Integer pressureKpa, Integer odometer,
                                         String notes, UUID measuredBy) {
        TyreMeasurement m = new TyreMeasurement();
        m.tenantId = tenantId;
        m.tyreId = tyreId;
        m.vehicleId = vehicleId;
        m.measuredAt = measuredAt;
        m.treadDepthMm = treadDepthMm;
        m.pressureKpa = pressureKpa;
        m.odometer = odometer;
        m.notes = notes;
        m.measuredBy = measuredBy;
        return m;
    }
}