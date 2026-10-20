package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One physical tyre for its whole life. It moves between vehicles and wheel positions; each stay
 * is a {@link TyreFitment}. {@code accumulatedKm} holds the kilometres from closed fitments —
 * add the current fitment's distance (see {@link #kmInService}) for the lifetime total.
 * <p>
 * Positions are free-form codes (FL, FR, RL, RR, A2LO, SPARE, ...). A vehicle can have only one
 * tyre per position, except spares (codes starting with SPARE), enforced by a partial unique index.
 */
@Entity
@Table(name = "fleet_tyres")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Tyre {

    /** At or below this the tyre is below / at the legal limit (South African road-traffic regulations: 1 mm). */
    public static final BigDecimal LEGAL_MINIMUM_MM = new BigDecimal("1.0");
    /** Below this, flag for replacement soon. A company-policy line — adjust to your own. */
    public static final BigDecimal REPLACE_SOON_MM = new BigDecimal("3.0");
    /** A new reading may not exceed the previous by more than this: tread does not grow back (a retread resets it). */
    private static final BigDecimal MAX_REGROWTH_MM = new BigDecimal("2.0");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(length = 100)
    private String pattern;

    @Column(nullable = false, length = 30)
    private String size;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    private String supplier;

    @Column(name = "initial_tread_depth_mm", precision = 4, scale = 1)
    private BigDecimal initialTreadDepthMm;

    @Column(name = "retread_count", nullable = false)
    private int retreadCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TyreStatus status = TyreStatus.IN_STOCK;

    @Column(name = "current_vehicle_id")
    private UUID currentVehicleId;

    @Column(name = "current_position", length = 10)
    private String currentPosition;

    @Column(name = "fitted_at")
    private Instant fittedAt;

    @Column(name = "fitted_odometer")
    private Integer fittedOdometer;

    @Column(name = "accumulated_km", nullable = false)
    private int accumulatedKm = 0;

    @Column(name = "last_tread_depth_mm", precision = 4, scale = 1)
    private BigDecimal lastTreadDepthMm;

    @Column(name = "last_measured_at")
    private Instant lastMeasuredAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "scrapped_at")
    private Instant scrappedAt;

    @Column(name = "scrap_reason", length = 500)
    private String scrapReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // ── Creation & edit ─────────────────────────────────────────────────

    public static Tyre create(TenantId tenantId, String serialNumber, String brand, String pattern, String size,
                              LocalDate purchaseDate, BigDecimal purchasePrice, String supplier,
                              BigDecimal initialTreadDepthMm, String notes) {
        Tyre t = new Tyre();
        t.tenantId = tenantId;
        t.createdAt = Instant.now();
        t.status = TyreStatus.IN_STOCK;
        t.updateDetails(serialNumber, brand, pattern, size, purchaseDate, purchasePrice, supplier,
                initialTreadDepthMm, notes);
        if (initialTreadDepthMm != null) {
            t.lastTreadDepthMm = initialTreadDepthMm;     // a new tyre's first reading is its new tread
            t.lastMeasuredAt = t.createdAt;
        }
        return t;
    }

    public void updateDetails(String serialNumber, String brand, String pattern, String size,
                              LocalDate purchaseDate, BigDecimal purchasePrice, String supplier,
                              BigDecimal initialTreadDepthMm, String notes) {
        this.serialNumber = serialNumber;
        this.brand = brand;
        this.pattern = pattern;
        this.size = size;
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.supplier = supplier;
        this.initialTreadDepthMm = initialTreadDepthMm;
        this.notes = notes;
        touch();
    }

    // ── Fitting ─────────────────────────────────────────────────────────

    public void fit(UUID vehicleId, String position, int odometer, Instant at) {
        changeStatus(TyreStatus.FITTED);
        this.currentVehicleId = vehicleId;
        this.currentPosition = position;
        this.fittedOdometer = odometer;
        this.fittedAt = at;
    }

    /**
     * Takes the tyre off its vehicle. {@code outcome} is where it goes next: IN_STOCK (reusable),
     * AWAITING_RETREAD, or SCRAPPED (which needs a reason). The distance run on this fitment is
     * added to the tyre's lifetime kilometres.
     */
    public void remove(int odometer, TyreStatus outcome, Instant at, String scrapReason) {
        if (status != TyreStatus.FITTED) {
            throw new IllegalStateException("Tyre " + serialNumber + " is " + status + ", not fitted");
        }
        if (outcome != TyreStatus.IN_STOCK && outcome != TyreStatus.AWAITING_RETREAD && outcome != TyreStatus.SCRAPPED) {
            throw new IllegalArgumentException("A removed tyre goes to IN_STOCK, AWAITING_RETREAD or SCRAPPED");
        }
        if (outcome == TyreStatus.SCRAPPED && scrapReason == null) {
            throw new IllegalArgumentException("A reason is required to scrap a tyre");
        }
        if (odometer < fittedOdometer) {
            throw new IllegalArgumentException("Removal odometer (" + odometer
                    + ") cannot be lower than the fitting odometer (" + fittedOdometer + ")");
        }
        accumulatedKm += odometer - fittedOdometer;
        changeStatus(outcome);
        this.currentVehicleId = null;
        this.currentPosition = null;
        this.fittedOdometer = null;
        this.fittedAt = null;
        if (outcome == TyreStatus.SCRAPPED) {
            this.scrappedAt = at;
            this.scrapReason = scrapReason;
        }
    }

    /**
     * Rotation is done in two phases by the service (to dodge the unique position index while
     * tyres swap): first {@code parkForRotation} gives every moving tyre a temporary position,
     * then {@code rotate} sets the final one. Kilometres on the old position are banked.
     */
    public void parkForRotation(String temporaryPosition) {
        requireFitted();
        this.currentPosition = temporaryPosition;
        touch();
    }

    public void rotate(String newPosition, int odometer, Instant at) {
        requireFitted();
        if (odometer < fittedOdometer) {
            throw new IllegalArgumentException("Rotation odometer (" + odometer
                    + ") cannot be lower than the fitting odometer (" + fittedOdometer + ")");
        }
        accumulatedKm += odometer - fittedOdometer;
        this.fittedOdometer = odometer;
        this.fittedAt = at;
        this.currentPosition = newPosition;
        touch();
    }

    // ── Wear, retread, scrap ────────────────────────────────────────────

    public void recordMeasurement(BigDecimal treadDepthMm, Instant at) {
        if (status == TyreStatus.SCRAPPED) {
            throw new IllegalStateException("Tyre " + serialNumber + " is scrapped");
        }
        if (lastTreadDepthMm != null && treadDepthMm.compareTo(lastTreadDepthMm.add(MAX_REGROWTH_MM)) > 0) {
            throw new IllegalArgumentException("Tread depth cannot grow: " + treadDepthMm + " mm is well above the last "
                    + "reading of " + lastTreadDepthMm + " mm. Check the reading, or record a retread.");
        }
        this.lastTreadDepthMm = treadDepthMm;
        this.lastMeasuredAt = at;
        touch();
    }

    /** Completes a retread: back to stock with a fresh tread and one more retread on its record. */
    public void completeRetread(BigDecimal newTreadDepthMm) {
        if (status != TyreStatus.AWAITING_RETREAD) {
            throw new IllegalStateException("Tyre " + serialNumber + " is " + status + ", not awaiting retread");
        }
        changeStatus(TyreStatus.IN_STOCK);
        this.retreadCount += 1;
        this.initialTreadDepthMm = newTreadDepthMm;
        this.lastTreadDepthMm = newTreadDepthMm;
        this.lastMeasuredAt = Instant.now();
    }

    /** Scraps a tyre that is not on a vehicle. (A fitted tyre is scrapped by removing it with outcome SCRAPPED.) */
    public void scrap(String reason, Instant at) {
        if (reason == null) throw new IllegalArgumentException("A reason is required to scrap a tyre");
        if (status == TyreStatus.FITTED) {
            throw new IllegalStateException("Remove the tyre from the vehicle first (outcome SCRAPPED)");
        }
        changeStatus(TyreStatus.SCRAPPED);
        this.scrappedAt = at;
        this.scrapReason = reason;
    }

    // ── Derived ─────────────────────────────────────────────────────────

    /** Lifetime kilometres: closed fitments plus the current one, given the vehicle's present odometer. */
    public int kmInService(Integer currentVehicleOdometer) {
        int current = (status == TyreStatus.FITTED && fittedOdometer != null && currentVehicleOdometer != null)
                ? Math.max(0, currentVehicleOdometer - fittedOdometer) : 0;
        return accumulatedKm + current;
    }

    public TreadStatus treadStatus() {
        return classify(lastTreadDepthMm);
    }

    /** Classifies any tread depth against the legal minimum and the replace-soon line. */
    public static TreadStatus classify(BigDecimal depthMm) {
        if (depthMm == null) return TreadStatus.NO_READING;
        if (depthMm.compareTo(LEGAL_MINIMUM_MM) <= 0) return TreadStatus.REPLACE_NOW;
        if (depthMm.compareTo(REPLACE_SOON_MM) < 0) return TreadStatus.REPLACE_SOON;
        return TreadStatus.OK;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void changeStatus(TyreStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot change tyre " + serialNumber + " from " + status + " to " + target);
        }
        this.status = target;
        touch();
    }

    private void requireFitted() {
        if (status != TyreStatus.FITTED) {
            throw new IllegalStateException("Tyre " + serialNumber + " is " + status + ", not fitted");
        }
    }

    private void touch() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}