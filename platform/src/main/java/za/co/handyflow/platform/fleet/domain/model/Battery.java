package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One physical battery for its whole life, moving between vehicles; each stay is a
 * {@link BatteryInstallation}. {@code daysInService} banks the days from closed installations —
 * add the current stay for the total (see {@link #totalDaysInService}). Warranty is a date, so the
 * expiry scheduler and the "is this failed battery still claimable" check are simple comparisons.
 */
@Entity
@Table(name = "fleet_batteries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Battery {

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
    private String model;

    @Column(name = "nominal_voltage", nullable = false)
    private int nominalVoltage;

    @Column(name = "capacity_ah")
    private Integer capacityAh;

    private String supplier;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "warranty_expiry")
    private LocalDate warrantyExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatteryStatus status = BatteryStatus.IN_STOCK;

    @Column(name = "current_vehicle_id")
    private UUID currentVehicleId;

    @Column(name = "current_position", length = 10)
    private String currentPosition;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "installed_odometer")
    private Integer installedOdometer;

    @Column(name = "days_in_service", nullable = false)
    private int daysInService = 0;

    @Column(name = "last_voltage", precision = 4, scale = 2)
    private BigDecimal lastVoltage;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_result", length = 10)
    private BatteryTestResult lastTestResult;

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

    public static Battery create(TenantId tenantId, String serialNumber, String brand, String model,
                                 int nominalVoltage, Integer capacityAh, String supplier, LocalDate purchaseDate,
                                 BigDecimal purchasePrice, LocalDate warrantyExpiry, String notes) {
        Battery b = new Battery();
        b.tenantId = tenantId;
        b.createdAt = Instant.now();
        b.status = BatteryStatus.IN_STOCK;
        b.updateDetails(serialNumber, brand, model, nominalVoltage, capacityAh, supplier, purchaseDate,
                purchasePrice, warrantyExpiry, notes);
        return b;
    }

    public void updateDetails(String serialNumber, String brand, String model, int nominalVoltage,
                              Integer capacityAh, String supplier, LocalDate purchaseDate,
                              BigDecimal purchasePrice, LocalDate warrantyExpiry, String notes) {
        if (nominalVoltage != 12 && nominalVoltage != 24) {
            throw new IllegalArgumentException("Nominal voltage must be 12 or 24");
        }
        if (purchaseDate != null && warrantyExpiry != null && warrantyExpiry.isBefore(purchaseDate)) {
            throw new IllegalArgumentException("The warranty cannot expire before the purchase date");
        }
        this.serialNumber = serialNumber;
        this.brand = brand;
        this.model = model;
        this.nominalVoltage = nominalVoltage;
        this.capacityAh = capacityAh;
        this.supplier = supplier;
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.warrantyExpiry = warrantyExpiry;
        this.notes = notes;
        touch();
    }

    public void install(UUID vehicleId, String position, int odometer, Instant at) {
        changeStatus(BatteryStatus.INSTALLED);
        this.currentVehicleId = vehicleId;
        this.currentPosition = position;
        this.installedOdometer = odometer;
        this.installedAt = at;
    }

    /**
     * Takes the battery off its vehicle. {@code outcome}: IN_STOCK (still good), FAULTY (failed,
     * awaiting a warranty claim or disposal) or SCRAPPED (needs a reason). The days it was installed
     * are banked into {@code daysInService}.
     */
    public void remove(int odometer, BatteryStatus outcome, Instant at, String scrapReason) {
        if (status != BatteryStatus.INSTALLED) {
            throw new IllegalStateException("Battery " + serialNumber + " is " + status + ", not installed");
        }
        if (outcome != BatteryStatus.IN_STOCK && outcome != BatteryStatus.FAULTY && outcome != BatteryStatus.SCRAPPED) {
            throw new IllegalArgumentException("A removed battery goes to IN_STOCK, FAULTY or SCRAPPED");
        }
        if (outcome == BatteryStatus.SCRAPPED && scrapReason == null) {
            throw new IllegalArgumentException("A reason is required to scrap a battery");
        }
        if (odometer < installedOdometer) {
            throw new IllegalArgumentException("Removal odometer (" + odometer
                    + ") cannot be lower than the installation odometer (" + installedOdometer + ")");
        }
        if (at.isBefore(installedAt)) {
            throw new IllegalArgumentException("Removal cannot be before the installation");
        }
        daysInService += (int) ChronoUnit.DAYS.between(installedAt, at);
        changeStatus(outcome);
        this.currentVehicleId = null;
        this.currentPosition = null;
        this.installedAt = null;
        this.installedOdometer = null;
        if (outcome == BatteryStatus.SCRAPPED) {
            this.scrappedAt = at;
            this.scrapReason = scrapReason;
        }
    }

    /** Records a resting-voltage test and returns its classified result. */
    public BatteryTestResult recordTest(BigDecimal voltage, Instant at) {
        if (status == BatteryStatus.SCRAPPED) {
            throw new IllegalStateException("Battery " + serialNumber + " is scrapped");
        }
        BatteryTestResult result = BatteryTestResult.classify(voltage, nominalVoltage);
        this.lastVoltage = voltage;
        this.lastTestedAt = at;
        this.lastTestResult = result;
        touch();
        return result;
    }

    /** Scraps a battery that is not installed. (An installed one is scrapped by removing it with outcome SCRAPPED.) */
    public void scrap(String reason, Instant at) {
        if (reason == null) throw new IllegalArgumentException("A reason is required to scrap a battery");
        if (status == BatteryStatus.INSTALLED) {
            throw new IllegalStateException("Remove the battery from the vehicle first (outcome SCRAPPED)");
        }
        changeStatus(BatteryStatus.SCRAPPED);
        this.scrappedAt = at;
        this.scrapReason = reason;
    }

    // ── Derived ─────────────────────────────────────────────────────────

    public int totalDaysInService(Instant now) {
        int current = (status == BatteryStatus.INSTALLED && installedAt != null)
                ? (int) Math.max(0, ChronoUnit.DAYS.between(installedAt, now)) : 0;
        return daysInService + current;
    }

    public boolean isInWarranty(LocalDate today) {
        return warrantyExpiry != null && !warrantyExpiry.isBefore(today);
    }

    private void changeStatus(BatteryStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot change battery " + serialNumber + " from " + status + " to " + target);
        }
        this.status = target;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}