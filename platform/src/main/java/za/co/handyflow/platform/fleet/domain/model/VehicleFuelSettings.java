package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-vehicle fuel-analytics settings. {@code expectedLitresPer100Km}, when set, replaces the
 * vehicle's rolling baseline as the yardstick for "abnormal" — useful for a vehicle that has
 * always been inefficient (its own history would never flag it). {@code tolerancePercent}
 * widens or narrows how far from the yardstick counts as abnormal.
 */
@Entity
@Table(name = "fleet_vehicle_fuel_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleFuelSettings {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false, unique = true)
    private UUID vehicleId;

    @Column(name = "expected_litres_per_100km", precision = 5, scale = 1)
    private BigDecimal expectedLitresPer100Km;

    @Column(name = "tolerance_percent")
    private Integer tolerancePercent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static VehicleFuelSettings create(TenantId tenantId, UUID vehicleId) {
        VehicleFuelSettings s = new VehicleFuelSettings();
        s.tenantId = tenantId;
        s.vehicleId = vehicleId;
        s.updatedAt = Instant.now();
        return s;
    }

    /** Full replace; nulls clear the override / revert to the default tolerance. */
    public void apply(BigDecimal expectedLitresPer100Km, Integer tolerancePercent, UUID updatedBy) {
        if (expectedLitresPer100Km != null && expectedLitresPer100Km.signum() <= 0) {
            throw new IllegalArgumentException("Expected consumption must be greater than zero");
        }
        if (tolerancePercent != null && (tolerancePercent < 5 || tolerancePercent > 100)) {
            throw new IllegalArgumentException("Tolerance must be between 5 and 100 percent");
        }
        this.expectedLitresPer100Km = expectedLitresPer100Km;
        this.tolerancePercent = tolerancePercent;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}