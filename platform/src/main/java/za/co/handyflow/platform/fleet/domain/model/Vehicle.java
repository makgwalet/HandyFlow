package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fleet_vehicles")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Vehicle {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private String registration;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    private Integer year;
    private String colour;
    private String vin;

    @Column(name = "vehicle_type", nullable = false)
    private String vehicleType;

    // FIX: was a raw String, set unconditionally by updateStatus(String) with
    // zero transition validation — see VehicleStatus's Javadoc for the full
    // rationale. Same fix as earthmoving's AssetStatus.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType = "DIESEL";

    @Column(name = "licence_disc_expiry")
    private LocalDate licenceDiscExpiry;

    @Column(name = "roadworthy_expiry")
    private LocalDate roadworthyExpiry;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "current_odometer", nullable = false)
    private Integer currentOdometer = 0;

    @Column(name = "last_service_km", nullable = false)
    private Integer lastServiceKm = 0;

    // NEW: without this, isDueForService()'s day-based check had nothing to
    // compare serviceIntervalDays against — the "time-based interval"
    // feature was accepted on the create form and stored, but structurally
    // could never actually trigger. See recordService() for where this gets set.
    @Column(name = "last_service_date")
    private LocalDate lastServiceDate;

    @Column(name = "service_interval_km", nullable = false)
    private Integer serviceIntervalKm = 10000;

    @Column(name = "service_interval_days")
    private Integer serviceIntervalDays;

    @Column(name = "tank_capacity_litres", precision = 8, scale = 2)
    private BigDecimal tankCapacityLitres;

    @Column(name = "daily_rate", precision = 15, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "assigned_driver_name")
    private String assignedDriverName;

    // NEW: the real link to a Driver record. assignedDriverName is kept as
    // a free-text fallback for display and for cases with no linked driver
    // yet (e.g. historical data, or a temporary/contract driver not worth
    // registering as a full Driver record) — this field is what actually
    // enables driver-side compliance tracking and notifications once set.
    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    private String notes;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    // ── Factory ───────────────────────────────────────────────────────────

    public static Vehicle create(TenantId tenantId,
                                 String registration,
                                 String make, String model, Integer year,
                                 String colour, String vin,
                                 String vehicleType, String fuelType,
                                 LocalDate licenceDiscExpiry,
                                 LocalDate roadworthyExpiry,
                                 LocalDate insuranceExpiry,
                                 BigDecimal dailyRate,
                                 BigDecimal tankCapacityLitres,
                                 Integer serviceIntervalKm,
                                 Integer serviceIntervalDays,
                                 String assignedDriverName,
                                 String notes) {
        Vehicle v = new Vehicle();
        v.tenantId = tenantId;
        v.registration = registration.toUpperCase().trim();
        v.make = make;
        v.model = model;
        v.year = year;
        v.colour = colour;
        v.vin = vin;
        v.vehicleType = vehicleType;
        v.fuelType = fuelType != null ? fuelType : "DIESEL";
        v.licenceDiscExpiry = licenceDiscExpiry;
        v.roadworthyExpiry = roadworthyExpiry;
        v.insuranceExpiry = insuranceExpiry;
        v.dailyRate = dailyRate;
        v.tankCapacityLitres = tankCapacityLitres;
        v.serviceIntervalKm = serviceIntervalKm != null ? serviceIntervalKm : 10000;
        v.serviceIntervalDays = serviceIntervalDays;
        v.assignedDriverName = assignedDriverName;
        v.notes = notes;
        v.status = VehicleStatus.AVAILABLE;
        v.currentOdometer = 0;
        v.lastServiceKm = 0;
        v.createdAt = Instant.now();
        v.updatedAt = Instant.now();
        return v;
    }

    // ── State transitions ────────────────────────────────────────────────
    // Every public transition method funnels through this single guard, same
    // pattern as earthmoving's EarthAsset.changeStatus().

    private void changeStatus(VehicleStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidVehicleStatusTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    /** Used by the generic status endpoint. For ON_TRIP, prefer startTripStatus() via FleetService.startTrip(). */
    public void changeStatusTo(VehicleStatus target) {
        changeStatus(target);
    }

    public void startTripStatus() {
        changeStatus(VehicleStatus.ON_TRIP);
    }

    public void endTripStatus() {
        changeStatus(VehicleStatus.AVAILABLE);
    }

    public void sendToMaintenance() {
        changeStatus(VehicleStatus.MAINTENANCE);
    }

    public void breakdown() {
        changeStatus(VehicleStatus.BREAKDOWN);
    }

    public void retire() {
        changeStatus(VehicleStatus.RETIRED);
    }

    // ── Domain logic ──────────────────────────────────────────────────────

    /**
     * FIX: previously, once the km-based interval was crossed, the method
     * short-circuited with a bare {@code return true} and a comment saying
     * the day-based check "requires a separate field" — meaning
     * serviceIntervalDays was accepted from the create form, stored, and
     * then never actually consulted by anything. Due is now genuinely
     * "km OR days, whichever comes first" — the correct semantics for a
     * backstop interval (e.g. "every 10,000km OR 180 days, whichever is sooner"
     * catches a vehicle that's driven very little but has sat for 6 months).
     */
    public boolean isDueForService() {
        boolean kmDue = (currentOdometer - lastServiceKm) >= serviceIntervalKm;
        if (kmDue) return true;

        if (serviceIntervalDays != null && lastServiceDate != null) {
            long daysSinceService = java.time.temporal.ChronoUnit.DAYS.between(lastServiceDate, LocalDate.now());
            return daysSinceService >= serviceIntervalDays;
        }
        return false;
    }

    public boolean isLicenceExpiringSoon() {
        return licenceDiscExpiry != null && licenceDiscExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public boolean isRoadworthyExpiringSoon() {
        return roadworthyExpiry != null && roadworthyExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public boolean isInsuranceExpiringSoon() {
        return insuranceExpiry != null && insuranceExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    /**
     * FIX: previously accepted any int with no floor check — an odometer
     * reading typo (e.g. transposing digits: 45000 -> 4500) would silently
     * corrupt the vehicle's distance/service-due tracking. Same guard as
     * earthmoving's EarthAsset.updateHours().
     */
    public void updateOdometer(int newOdometer) {
        if (newOdometer < this.currentOdometer) {
            throw new IllegalArgumentException(
                    "New odometer reading (" + newOdometer + ") cannot be lower than the current reading ("
                            + currentOdometer + "). Odometers only count up.");
        }
        this.currentOdometer = newOdometer;
        this.updatedAt = Instant.now();
    }

    public void recordService(int odometerAtService) {
        this.lastServiceKm = odometerAtService;
        this.lastServiceDate = LocalDate.now();
        this.updatedAt = Instant.now();
    }

    /** Pass null to unassign. */
    public void assignDriver(UUID driverId) {
        this.assignedDriverId = driverId;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
