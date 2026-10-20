package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A snapshot of an asset's hour meters at a moment. Immutable; consecutive readings give the hours worked between them. */
@Entity
@Table(name = "fleet_asset_meter_readings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AssetMeterReading {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "engine_hours", nullable = false, precision = 10, scale = 1)
    private BigDecimal engineHours;

    @Column(name = "pto_hours", precision = 10, scale = 1)
    private BigDecimal ptoHours;

    @Column(name = "hydraulic_hours", precision = 10, scale = 1)
    private BigDecimal hydraulicHours;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    public static AssetMeterReading record(TenantId tenantId, UUID vehicleId, Instant recordedAt,
                                           BigDecimal engineHours, BigDecimal ptoHours, BigDecimal hydraulicHours,
                                           UUID recordedBy) {
        AssetMeterReading r = new AssetMeterReading();
        r.tenantId = tenantId;
        r.vehicleId = vehicleId;
        r.recordedAt = recordedAt;
        r.engineHours = engineHours;
        r.ptoHours = ptoHours;
        r.hydraulicHours = hydraulicHours;
        r.recordedBy = recordedBy;
        return r;
    }
}