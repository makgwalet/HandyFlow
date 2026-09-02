package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A generic physical sub-division of a farm — camp, field, paddock, house,
 * pen, pond, orchard. DELIBERATE CONSOLIDATION: rather than separate
 * {@code AgField}/{@code AgCamp}/{@code AgHouse}/{@code AgPond} entities (a
 * literal reading of the product vision doc's per-domain terminology),
 * this single entity typed by {@code areaType} covers all of them — they
 * share every structural property (a bounded area on the farm that holds
 * animals, birds, fish, or plants) and only differ in which later
 * sub-domain (Livestock now; Poultry/Aquaculture/Crops later) uses them
 * and what vocabulary that sub-domain's UI labels the type with.
 */
@Entity
@Table(name = "ag_production_areas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgProductionArea {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false)
    private String name;

    /** CAMP | FIELD | PADDOCK | HOUSE | PEN | POND | ORCHARD | OTHER */
    @Column(name = "area_type", nullable = false)
    private String areaType;

    @Column(name = "size_hectares", precision = 10, scale = 2)
    private BigDecimal sizeHectares;

    /** Nullable — max head/birds/animals this area is rated for, where that's a meaningful number. */
    private Integer capacity;

    private String soilType;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | RESTING | QUARANTINE | INACTIVE

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static AgProductionArea create(TenantId tenantId, UUID farmId, String name, String areaType,
                                           BigDecimal sizeHectares, Integer capacity, String soilType) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (areaType == null || areaType.isBlank()) throw new IllegalArgumentException("areaType is required");

        AgProductionArea a = new AgProductionArea();
        a.tenantId = tenantId;
        a.farmId = farmId;
        a.name = name;
        a.areaType = areaType;
        a.sizeHectares = sizeHectares;
        a.capacity = capacity;
        a.soilType = soilType;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(String name, String areaType, BigDecimal sizeHectares, Integer capacity,
                        String soilType, String notes) {
        if (name != null && !name.isBlank()) this.name = name;
        if (areaType != null && !areaType.isBlank()) this.areaType = areaType;
        this.sizeHectares = sizeHectares;
        this.capacity = capacity;
        this.soilType = soilType;
        this.notes = notes;
    }

    public void changeStatus(String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        this.status = status;
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
