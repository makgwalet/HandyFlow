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
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
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

    @Column(nullable = false)
    private String status = "AVAILABLE";

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

    @Column(name = "service_interval_km", nullable = false)
    private Integer serviceIntervalKm = 10000;

    // NEW: time-based service interval — "every 180 days regardless of km"
    @Column(name = "service_interval_days")
    private Integer serviceIntervalDays;

    @Column(name = "tank_capacity_litres", precision = 8, scale = 2)
    private BigDecimal tankCapacityLitres;

    @Column(name = "daily_rate", precision = 15, scale = 2)
    private BigDecimal dailyRate;

    // NEW: the person primarily responsible for this vehicle
    @Column(name = "assigned_driver_name")
    private String assignedDriverName;

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

    // ── Factory ───────────────────────────────────────────────────────────────

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
        v.tenantId            = tenantId;
        v.registration        = registration.toUpperCase().trim();
        v.make                = make;
        v.model               = model;
        v.year                = year;
        v.colour              = colour;
        v.vin                 = vin;
        v.vehicleType         = vehicleType;
        v.fuelType            = fuelType != null ? fuelType : "DIESEL";
        v.licenceDiscExpiry   = licenceDiscExpiry;
        v.roadworthyExpiry    = roadworthyExpiry;
        v.insuranceExpiry     = insuranceExpiry;
        v.dailyRate           = dailyRate;
        v.tankCapacityLitres  = tankCapacityLitres;
        v.serviceIntervalKm   = serviceIntervalKm != null ? serviceIntervalKm : 10000;
        v.serviceIntervalDays = serviceIntervalDays;
        v.assignedDriverName  = assignedDriverName;
        v.notes               = notes;
        v.status              = "AVAILABLE";
        v.currentOdometer     = 0;
        v.lastServiceKm       = 0;
        v.createdAt           = Instant.now();
        v.updatedAt           = Instant.now();
        return v;
    }

    // ── Domain logic ──────────────────────────────────────────────────────────

    public boolean isDueForService() {
        boolean kmDue = (currentOdometer - lastServiceKm) >= serviceIntervalKm;
        if (!kmDue || serviceIntervalDays == null) return kmDue;
        // Also check time-based interval if configured
        // (lastServiceDate tracking requires a separate field — for now use km only)
        return true;
    }

    public boolean isLicenceExpiringSoon() {
        return licenceDiscExpiry != null &&
                licenceDiscExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public boolean isRoadworthyExpiringSoon() {
        return roadworthyExpiry != null &&
                roadworthyExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public boolean isInsuranceExpiringSoon() {
        return insuranceExpiry != null &&
                insuranceExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public void updateOdometer(int newOdometer) {
        this.currentOdometer = newOdometer;
        this.updatedAt = Instant.now();
    }

    public void recordService(int odometerAtService) {
        this.lastServiceKm = odometerAtService;
        this.updatedAt     = Instant.now();
    }

    public void updateStatus(String status) {
        this.status    = status;
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
