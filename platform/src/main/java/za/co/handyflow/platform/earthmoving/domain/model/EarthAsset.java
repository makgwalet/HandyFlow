// earthmoving/domain/model/EarthAsset.java

package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "earthmoving_assets")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EarthAsset {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private String name;

    private String make;
    private String model;
    private Integer year;

    @Column(name = "serial_number")
    private String serialNumber;

    private String registration;

    @Column(name = "asset_type", nullable = false)
    private String assetType;

    @Column(nullable = false)
    private String status = "AVAILABLE";

    @Column(name = "hourly_rate", precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "daily_rate", precision = 15, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "current_hours", precision = 10, scale = 1)
    private BigDecimal currentHours = BigDecimal.ZERO;

    @Column(name = "last_service_hours", precision = 10, scale = 1)
    private BigDecimal lastServiceHours = BigDecimal.ZERO;

    @Column(name = "service_interval_hours", precision = 10, scale = 1)
    private BigDecimal serviceIntervalHours = new BigDecimal("250");

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

    @Column(name = "fleet_number")
    private String fleetNumber;         // e.g. "D9-001", "CAT-EX-003"

    @Column(name = "ownership_type")
    private String ownershipType = "OWN";  // OWN | HIRED_IN | HIRED_OUT

    @Column(name = "hire_supplier")
    private String hireSupplier;        // HIRED_IN: name of company that owns the machine

    @Column(name = "hire_start_date")
    private java.time.LocalDate hireStartDate;

    @Column(name = "hire_end_date")
    private java.time.LocalDate hireEndDate;

    @Column(name = "current_site")
    private String currentSite;         // Where the machine is deployed right now

    @Column(name = "current_client")
    private String currentClient;

    @Version
    private Long version;

    public static EarthAsset create(TenantId tenantId, String name, String fleetNumber,
                                    String assetType, String make, String model,
                                    Integer year, String serialNumber, String registration,
                                    String ownershipType, String hireSupplier,
                                    java.time.LocalDate hireStartDate,
                                    java.time.LocalDate hireEndDate,
                                    java.math.BigDecimal dailyRate,
                                    java.math.BigDecimal hourlyRate,
                                    String notes) {
        EarthAsset a = new EarthAsset();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.name = name;
        a.fleetNumber = fleetNumber;
        a.assetType = assetType;
        a.make = make;
        a.model = model;
        a.year = year;
        a.serialNumber = serialNumber;
        a.registration = registration;
        a.ownershipType = ownershipType != null ? ownershipType : "OWN";
        a.hireSupplier = hireSupplier;
        a.hireStartDate = hireStartDate;
        a.hireEndDate = hireEndDate;
        a.dailyRate = dailyRate;
        a.hourlyRate = hourlyRate;
        a.notes = notes;
        a.status = "AVAILABLE";
        a.currentHours = java.math.BigDecimal.ZERO;
        a.lastServiceHours = java.math.BigDecimal.ZERO;
        a.serviceIntervalHours = new java.math.BigDecimal("250");
        a.createdAt = java.time.Instant.now();
        a.updatedAt = java.time.Instant.now();
        return a;
    }

    public void deploy() {
        this.status    = "DEPLOYED";
        this.updatedAt = Instant.now();
    }

    public void returnToYard() {
        this.status    = "AVAILABLE";
        this.currentSite = null;
        this.currentClient = null;
        this.updatedAt = Instant.now();
    }

    public void sendToMaintenance() {
        this.status    = "MAINTENANCE";
        this.updatedAt = Instant.now();
    }

    public void retire() {
        this.status    = "RETIRED";
        this.updatedAt = Instant.now();
    }

    public void updateHours(BigDecimal newHours) {
        this.currentHours = newHours;
        this.updatedAt    = Instant.now();
    }

    public boolean isDueForService() {
        if (currentHours == null || lastServiceHours == null || serviceIntervalHours == null)
            return false;
        return currentHours.subtract(lastServiceHours)
                .compareTo(serviceIntervalHours) >= 0;
    }

    public void recordService(BigDecimal hoursAtService) {
        this.lastServiceHours = hoursAtService;
        this.updatedAt        = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public void breakdown() {
        this.status = "BREAKDOWN";
    }

    public void hireOut() {
        this.status = "HIRED_OUT";
    }

    public void deployTo(String siteName, String clientName) {
        this.status = "DEPLOYED";
        this.currentSite = siteName;
        this.currentClient = clientName;
    }




    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }


}