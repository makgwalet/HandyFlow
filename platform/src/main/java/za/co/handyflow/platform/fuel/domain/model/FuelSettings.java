package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** One row per tenant: the rates cost-to-serve uses and the supplier-delivery tolerance. All default to "not configured". */
@Entity
@Table(name = "fuel_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSettings {

    public static final BigDecimal DEFAULT_TRIP_HOURS = new BigDecimal("2.00");
    public static final BigDecimal DEFAULT_TOLERANCE_PCT = new BigDecimal("2.00");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "driver_cost_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal driverCostPerHour = BigDecimal.ZERO;

    @Column(name = "vehicle_cost_per_km", nullable = false, precision = 10, scale = 4)
    private BigDecimal vehicleCostPerKm = BigDecimal.ZERO;

    @Column(name = "fixed_cost_per_delivery", nullable = false, precision = 10, scale = 2)
    private BigDecimal fixedCostPerDelivery = BigDecimal.ZERO;

    @Column(name = "overhead_pct_of_revenue", nullable = false, precision = 5, scale = 2)
    private BigDecimal overheadPctOfRevenue = BigDecimal.ZERO;

    @Column(name = "default_trip_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultTripHours = DEFAULT_TRIP_HOURS;

    @Column(name = "po_over_tolerance_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal poOverTolerancePct = DEFAULT_TOLERANCE_PCT;

    @Column(name = "require_supplier_approval", nullable = false)
    private boolean requireSupplierApproval = false;

    /** Stock adjustments of at least this many litres (either direction) need a second person. Null means no limit. */
    @Column(name = "adjustment_threshold_litres", precision = 14, scale = 3)
    private BigDecimal adjustmentThresholdLitres;

    /** Loaded-versus-delivered difference (as a percentage of litres loaded) above which a variance is raised. Null: not set, nothing is flagged. */
    @Column(name = "variance_tolerance_pct", precision = 5, scale = 2)
    private BigDecimal varianceTolerancePct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static FuelSettings forTenant(TenantId tenantId) {
        FuelSettings s = new FuelSettings();
        s.tenantId = tenantId;
        return s;
    }

    public void update(BigDecimal driverPerHour, BigDecimal vehiclePerKm, BigDecimal fixedPerDelivery, BigDecimal overheadPct,
                       BigDecimal tripHours, BigDecimal toleranceMaxPct) {
        this.driverCostPerHour = nonNegative(driverPerHour, "Driver cost per hour");
        this.vehicleCostPerKm = nonNegative(vehiclePerKm, "Vehicle cost per km");
        this.fixedCostPerDelivery = nonNegative(fixedPerDelivery, "Fixed cost per delivery");
        BigDecimal oh = overheadPct == null ? BigDecimal.ZERO : overheadPct;
        if (oh.signum() < 0 || oh.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("Overhead must be between 0% and 100% of revenue.");
        this.overheadPctOfRevenue = oh;
        BigDecimal hrs = tripHours == null ? DEFAULT_TRIP_HOURS : tripHours;
        if (hrs.signum() <= 0 || hrs.compareTo(new BigDecimal("24")) > 0) throw new IllegalArgumentException("Typical trip hours must be more than 0 and at most 24.");
        this.defaultTripHours = hrs;
        BigDecimal tol = toleranceMaxPct == null ? DEFAULT_TOLERANCE_PCT : toleranceMaxPct;
        if (tol.signum() < 0 || tol.compareTo(new BigDecimal("10")) > 0) throw new IllegalArgumentException("Delivery tolerance must be between 0% and 10%.");
        this.poOverTolerancePct = tol;
        this.updatedAt = Instant.now();
    }

    public void updateControls(boolean requireSupplierApproval, BigDecimal adjustmentThresholdLitres) {
        if (adjustmentThresholdLitres != null && adjustmentThresholdLitres.signum() <= 0) {
            throw new IllegalArgumentException("The adjustment threshold must be more than 0 litres, or left empty for no limit.");
        }
        this.requireSupplierApproval = requireSupplierApproval;
        this.adjustmentThresholdLitres = adjustmentThresholdLitres;
        this.updatedAt = Instant.now();
    }

    public void updateVarianceTolerance(BigDecimal pct) {
        if (pct != null && (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0)) throw new IllegalArgumentException("The variance tolerance must be between 0% and 100%, or empty to switch the check off.");
        this.varianceTolerancePct = pct;
        this.updatedAt = Instant.now();
    }

    /** True when an adjustment of this size needs a second person. */
    public boolean adjustmentNeedsApproval(BigDecimal litres) {
        return adjustmentThresholdLitres != null && litres != null && litres.abs().compareTo(adjustmentThresholdLitres) >= 0;
    }

    /** The tolerance as a fraction (2% is 0.02). */
    public BigDecimal poToleranceFraction() {
        return poOverTolerancePct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }

    /** True when no cost rate is set, so cost-to-serve would equal plain margin. */
    public boolean ratesUnset() {
        return driverCostPerHour.signum() == 0 && vehicleCostPerKm.signum() == 0 && fixedCostPerDelivery.signum() == 0 && overheadPctOfRevenue.signum() == 0;
    }

    private static BigDecimal nonNegative(BigDecimal v, String label) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        if (x.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative.");
        return x;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
