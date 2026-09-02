package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A physical building asset at a site — HVAC plant, generator, fire
 * extinguisher/hose reel, elevator, electrical DB, plumbing infrastructure,
 * etc. {@code assetTag} is user-supplied (matches {@code EarthAsset}'s own
 * {@code fleetNumber} and {@code Vehicle}'s own {@code registration} — both
 * user-supplied identifiers, not system-generated sequences), unique per
 * tenant when provided.
 * <p>
 * Status transitions are guarded the same way {@code EarthAsset}/
 * {@code Vehicle} guard theirs, but via a plain {@code IllegalStateException}
 * rather than a dedicated exception subclass — matching the simpler
 * convention used by every provider-style module built this engagement
 * (Warehousing, TrainingProvider, etc.) rather than earthmoving/fleet's own
 * heavier {@code InvalidXStatusTransitionException} machinery, which isn't
 * otherwise used anywhere in this codebase outside those two modules.
 */
@Entity
@Table(name = "facility_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityAsset {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "asset_tag")
    private String assetTag;

    @Column(nullable = false)
    private String name;

    @Column(name = "asset_type", nullable = false)
    private String assetType; // HVAC, GENERATOR, FIRE_EQUIPMENT, ELEVATOR, ELECTRICAL, PLUMBING, OTHER

    private String location; // free-text location within the site, e.g. "Roof - East Wing"

    private String manufacturer;
    private String model;
    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "install_date")
    private LocalDate installDate;

    @Column(name = "warranty_expiry_date")
    private LocalDate warrantyExpiryDate;

    @Column(nullable = false)
    private String criticality = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false)
    private String status = "OPERATIONAL"; // OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED

    private String notes;

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

    public static FacilityAsset create(TenantId tenantId, UUID siteId, String assetTag, String name,
                                        String assetType, String location, String manufacturer, String model,
                                        String serialNumber, LocalDate installDate, LocalDate warrantyExpiryDate,
                                        String criticality, String notes) {
        FacilityAsset a = new FacilityAsset();
        a.tenantId = tenantId;
        a.siteId = siteId;
        a.assetTag = assetTag;
        a.name = name;
        a.assetType = assetType != null ? assetType.toUpperCase() : "OTHER";
        a.location = location;
        a.manufacturer = manufacturer;
        a.model = model;
        a.serialNumber = serialNumber;
        a.installDate = installDate;
        a.warrantyExpiryDate = warrantyExpiryDate;
        a.criticality = criticality != null ? criticality.toUpperCase() : "MEDIUM";
        a.notes = notes;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(String name, String location, String manufacturer, String model,
                        String serialNumber, LocalDate warrantyExpiryDate, String criticality, String notes) {
        if (name != null) this.name = name;
        this.location = location;
        this.manufacturer = manufacturer;
        this.model = model;
        this.serialNumber = serialNumber;
        this.warrantyExpiryDate = warrantyExpiryDate;
        if (criticality != null) this.criticality = criticality.toUpperCase();
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void markDown() {
        requireNotDecommissioned();
        this.status = "DOWN";
        this.updatedAt = Instant.now();
    }

    public void sendToMaintenance() {
        requireNotDecommissioned();
        this.status = "MAINTENANCE";
        this.updatedAt = Instant.now();
    }

    public void markOperational() {
        requireNotDecommissioned();
        this.status = "OPERATIONAL";
        this.updatedAt = Instant.now();
    }

    public void decommission() {
        if ("DECOMMISSIONED".equals(status))
            throw new IllegalStateException("Asset is already decommissioned");
        this.status = "DECOMMISSIONED";
        this.updatedAt = Instant.now();
    }

    private void requireNotDecommissioned() {
        if ("DECOMMISSIONED".equals(status))
            throw new IllegalStateException("Cannot change status of a decommissioned asset");
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isOperational() { return "OPERATIONAL".equals(status); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
