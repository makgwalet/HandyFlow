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

    /** Forecourt takings variance (rand) above which a shift needs a reason and a supervisor's review. */
    @Column(name = "cash_variance_tolerance", nullable = false, precision = 10, scale = 2)
    private BigDecimal cashVarianceTolerance = new BigDecimal("5.00");

    /** When on, the person who closes a shift must not be the person who opened it. */
    @Column(name = "require_separate_closer", nullable = false)
    private boolean requireSeparateCloser = false;

    /** A new price that moves more than this percentage from the price it replaces waits for approval. Null: off. */
    @Column(name = "price_change_approval_pct", precision = 6, scale = 2)
    private BigDecimal priceChangeApprovalPct;

    /** A new price whose margin over the latest purchase cost is below this percentage waits for approval. Null: off. */
    @Column(name = "min_margin_pct", precision = 6, scale = 2)
    private BigDecimal minMarginPct;

    /** Comma-separated licensed activities (e.g. "WHOLESALE,STORAGE") whose expiry on the loading depot's legal entity blocks allocation. Empty = off. */
    @Column(name = "licence_blocking_activities", nullable = false, length = 120)
    private String licenceBlockingActivities = "";

    /** Opt-in: tell the administrators once when an order passes its expected date without being fully received. */
    @Column(name = "alert_overdue_purchase_orders", nullable = false)
    private boolean alertOverduePurchaseOrders = false;

    /** Days an unresolved delivery variance may stay open before the administrators are told. Null: no alert. */
    @Column(name = "variance_review_days")
    private Integer varianceReviewDays;

    /** When on, a compartment with no product rating is a review point on every allocation. */
    @Column(name = "require_compartment_rating", nullable = false)
    private boolean requireCompartmentRating = false;

    /** When on, a cleaning clears a changeover only once its certificate is uploaded and verified by someone else. */
    @Column(name = "require_verified_cleaning", nullable = false)
    private boolean requireVerifiedCleaning = false;

    /** When on, imported shifts are dated in shifts-by-date reports by the closing time their provider reported. */
    @Column(name = "shift_report_use_provider_times", nullable = false)
    private boolean shiftReportUseProviderTimes = false;

    /** The timezone that decides which day a shift belongs to in those reports. Null: Africa/Johannesburg. */
    @Column(name = "report_timezone", length = 60)
    private String reportTimezone;

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

    /** Null arguments leave the current value alone, so an older client that does not send them cannot reset them. */
    public void updateCashControls(BigDecimal tolerance, Boolean separateCloser) {
        if (tolerance != null) {
            if (tolerance.signum() < 0) throw new IllegalArgumentException("The cash tolerance cannot be negative.");
            this.cashVarianceTolerance = tolerance;
        }
        if (separateCloser != null) this.requireSeparateCloser = separateCloser;
        this.updatedAt = Instant.now();
    }

    /** Null leaves the current value alone; zero switches that check off. */
    public void updatePriceControls(BigDecimal changePct, BigDecimal minMargin) {
        if (changePct != null) {
            if (changePct.signum() < 0) throw new IllegalArgumentException("The price-change limit cannot be negative.");
            this.priceChangeApprovalPct = changePct.signum() == 0 ? null : changePct;
        }
        if (minMargin != null) {
            if (minMargin.signum() < 0 || minMargin.compareTo(new BigDecimal("100")) >= 0) throw new IllegalArgumentException("The minimum margin must be between 0% and under 100%.");
            this.minMarginPct = minMargin.signum() == 0 ? null : minMargin;
        }
        this.updatedAt = Instant.now();
    }

    /** Null leaves the current choice alone; an empty list switches the check off. */
    public void updateLicenceBlocking(java.util.List<String> activities) {
        if (activities == null) return;
        java.util.TreeSet<String> chosen = new java.util.TreeSet<>();
        for (String a : activities) {
            if (a == null || a.isBlank()) continue;
            String up = a.trim().toUpperCase();
            if (!FuelComplianceItem.ACTIVITIES.contains(up)) throw new IllegalArgumentException("Unknown licensed activity: " + a);
            chosen.add(up);
        }
        this.licenceBlockingActivities = String.join(",", chosen);
        this.updatedAt = Instant.now();
    }

    public void updateAutomation(boolean alertOverduePurchaseOrders) {
        this.alertOverduePurchaseOrders = alertOverduePurchaseOrders; this.updatedAt = Instant.now();
    }

    /** Days an unresolved variance may stay open before an alert, 1 to 365; null switches the alert off. There is no default. */
    public void updateVarianceReviewDays(Integer days) {
        if (days != null && (days < 1 || days > 365)) throw new IllegalArgumentException("Days must be between 1 and 365, or empty to switch the alert off.");
        this.varianceReviewDays = days; this.updatedAt = Instant.now();
    }

    public void updateReportBasis(boolean useProviderTimes, String timezone) {
        String tz = timezone == null || timezone.isBlank() ? null : timezone.trim();
        if (tz != null) { try { java.time.ZoneId.of(tz); } catch (RuntimeException e) { throw new IllegalArgumentException("Unknown timezone: " + tz); } }
        this.shiftReportUseProviderTimes = useProviderTimes; this.reportTimezone = tz; this.updatedAt = Instant.now();
    }
    public void updateRequireVerifiedCleaning(boolean on) { this.requireVerifiedCleaning = on; this.updatedAt = Instant.now(); }
    public void updateRequireCompartmentRating(boolean on) { this.requireCompartmentRating = on; this.updatedAt = Instant.now(); }

    public java.util.Set<String> blockingActivities() {
        if (licenceBlockingActivities == null || licenceBlockingActivities.isBlank()) return java.util.Set.of();
        return new java.util.TreeSet<>(java.util.Arrays.asList(licenceBlockingActivities.split(",")));
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
