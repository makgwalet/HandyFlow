// fleet/domain/model/Vehicle.java

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
    private Integer serviceIntervalKm = 15000;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType = "DIESEL";

    @Column(name = "tank_capacity_litres", precision = 8, scale = 2)
    private BigDecimal tankCapacityLitres;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "daily_rate", precision = 15, scale = 2)
    private BigDecimal dailyRate;

    private String notes;

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

    public static Vehicle create(TenantId tenantId, String registration,
                                 String make, String model, Integer year,
                                 String vehicleType, String fuelType,
                                 LocalDate licenceDiscExpiry) {
        Vehicle v = new Vehicle();
        v.tenantId          = tenantId;
        v.registration      = registration.toUpperCase().trim();
        v.make              = make;
        v.model             = model;
        v.year              = year;
        v.vehicleType       = vehicleType;
        v.fuelType          = fuelType != null ? fuelType : "DIESEL";
        v.licenceDiscExpiry = licenceDiscExpiry;
        v.status            = "AVAILABLE";
        v.currentOdometer   = 0;
        v.lastServiceKm     = 0;
        v.serviceIntervalKm = 15000;
        v.createdAt         = Instant.now();
        v.updatedAt         = Instant.now();
        return v;
    }

    public boolean isDueForService() {
        // WHY? Alert when km driven since last service exceeds interval
        return (currentOdometer - lastServiceKm) >= serviceIntervalKm;
    }

    public boolean isLicenceExpiringSoon() {
        // WHY 30 days? Standard lead time to renew disc
        return licenceDiscExpiry != null &&
                licenceDiscExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public boolean isRoadworthyExpiringSoon() {
        return roadworthyExpiry != null &&
                roadworthyExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public void updateOdometer(int newOdometer) {
        if (newOdometer < this.currentOdometer) {
            throw new IllegalArgumentException(
                    "New odometer reading cannot be less than current"
            );
        }
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