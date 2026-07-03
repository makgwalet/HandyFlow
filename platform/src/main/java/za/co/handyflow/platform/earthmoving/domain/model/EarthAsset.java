// earthmoving/domain/model/EarthAsset.java

package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthAsset {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
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

    // FIX: was a raw String. See AssetStatus for why that's a bug generator
    // and how transitions are now validated instead of trusted blindly.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.AVAILABLE;

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

    @Column(name = "ownership_type", nullable = false)
    private String ownershipType = "OWN";  // OWN | HIRED_IN | HIRED_OUT

    @Column(name = "hire_supplier")
    private String hireSupplier;        // HIRED_IN: name of company that owns the machine

    @Column(name = "hire_start_date")
    private LocalDate hireStartDate;

    @Column(name = "hire_end_date")
    private LocalDate hireEndDate;

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
                                    LocalDate hireStartDate, LocalDate hireEndDate,
                                    BigDecimal dailyRate, BigDecimal hourlyRate,
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
        a.status = AssetStatus.AVAILABLE;
        a.currentHours = BigDecimal.ZERO;
        a.lastServiceHours = BigDecimal.ZERO;
        a.serviceIntervalHours = new BigDecimal("250");
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    // ── State transitions ────────────────────────────────────────────────
    // Every public transition method now funnels through this single guard.
    // Add a new transition (e.g. a future "COMMISSIONING" state)? Update
    // AssetStatus's transition table and, if it needs side effects (like
    // clearing currentSite below), add a case here — one place, not seven.

    private void changeStatus(AssetStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidAssetStatusTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public void returnToYard() {
        changeStatus(AssetStatus.AVAILABLE);
        this.currentSite = null;
        this.currentClient = null;
    }

    public void sendToMaintenance() {
        changeStatus(AssetStatus.MAINTENANCE);
    }

    public void retire() {
        changeStatus(AssetStatus.RETIRED);
    }

    public void breakdown() {
        changeStatus(AssetStatus.BREAKDOWN);
    }

    public void hireOut() {
        changeStatus(AssetStatus.HIRED_OUT);
    }

    /** Deploy to a site/client. Only legal from AVAILABLE (see AssetStatus). */
    public void deployTo(String siteName, String clientName) {
        changeStatus(AssetStatus.DEPLOYED);
        this.currentSite = siteName;
        this.currentClient = clientName;
    }

    /**
     * Status-only transition to DEPLOYED, used by the generic
     * PATCH /status endpoint which carries no site/client data — unlike
     * {@link #deployTo}, this deliberately leaves currentSite/currentClient
     * untouched rather than blanking them out. Prefer the dedicated
     * {@code /deploy} endpoint ({@link #deployTo}) when you have site
     * details to record.
     */
    public void markDeployed() {
        changeStatus(AssetStatus.DEPLOYED);
    }

    // ── Other behaviour ──────────────────────────────────────────────────

    public void updateHours(BigDecimal newHours) {
        if (newHours != null && currentHours != null && newHours.compareTo(currentHours) < 0) {
            throw new IllegalArgumentException(
                    "New hour meter reading (" + newHours + ") cannot be lower than the current reading ("
                            + currentHours + "). Hour meters only count up.");
        }
        this.currentHours = newHours;
        this.updatedAt = Instant.now();
    }

    public boolean isDueForService() {
        if (currentHours == null || lastServiceHours == null || serviceIntervalHours == null)
            return false;
        return currentHours.subtract(lastServiceHours).compareTo(serviceIntervalHours) >= 0;
    }

    public void recordService(BigDecimal hoursAtService) {
        this.lastServiceHours = hoursAtService;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}