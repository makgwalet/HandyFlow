package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One planting instance — a specific crop, in a specific
 * {@link AgProductionArea}, in a specific {@link AgSeason} (optional) — the
 * Crops sub-domain's central tracking unit, playing the same structural
 * role {@link AgGroup} plays for Livestock. Every other Crops history
 * entity ({@link AgInputApplication}, {@link AgScoutingRecord},
 * {@link AgHarvestRecord}) references exactly one {@code AgCropCycle} —
 * there is no individual-vs-group duality here the way there is for
 * animals, because crops are never tracked as single plants; the cycle
 * itself IS the tracking granularity, confirmed as the right shape in the
 * architecture plan's own §5 framing ("Crops has no individual/group
 * distinction... it has fields, seasons, and input applications instead").
 * <p>
 * {@code seedInventoryItemId} is deliberately a field on the cycle itself
 * rather than modeled as one more {@link AgInputApplication} row — seed is
 * a one-time attribute of how a cycle started, not a repeated in-season
 * event the way fertiliser/pesticide applications are.
 */
@Entity
@Table(name = "ag_crop_cycles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgCropCycle {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "production_area_id", nullable = false)
    private UUID productionAreaId;

    @Column(name = "enterprise_id")
    private UUID enterpriseId;

    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "crop_type_id", nullable = false)
    private UUID cropTypeId;

    private String variety;

    @Column(name = "cycle_name")
    private String cycleName;

    @Column(name = "area_planted_hectares", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaPlantedHectares;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(name = "seed_inventory_item_id")
    private UUID seedInventoryItemId;

    @Column(name = "seed_quantity", precision = 12, scale = 3)
    private BigDecimal seedQuantity;

    @Column(name = "seed_source")
    private String seedSource;

    @Column(nullable = false)
    private String status = "PLANNED"; // PLANNED | PLANTED | GROWING | HARVESTING | HARVESTED | FAILED | ABANDONED

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

    public static AgCropCycle create(TenantId tenantId, UUID farmId, UUID productionAreaId, UUID enterpriseId,
                                      UUID seasonId, UUID cropTypeId, String variety, String cycleName,
                                      BigDecimal areaPlantedHectares, LocalDate plantingDate,
                                      LocalDate expectedHarvestDate, UUID seedInventoryItemId,
                                      BigDecimal seedQuantity, String seedSource, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (productionAreaId == null) throw new IllegalArgumentException("productionAreaId is required");
        if (cropTypeId == null) throw new IllegalArgumentException("cropTypeId is required");
        if (areaPlantedHectares == null || areaPlantedHectares.signum() <= 0) throw new IllegalArgumentException("areaPlantedHectares must be positive");

        AgCropCycle c = new AgCropCycle();
        c.tenantId = tenantId;
        c.farmId = farmId;
        c.productionAreaId = productionAreaId;
        c.enterpriseId = enterpriseId;
        c.seasonId = seasonId;
        c.cropTypeId = cropTypeId;
        c.variety = variety;
        c.cycleName = cycleName;
        c.areaPlantedHectares = areaPlantedHectares;
        c.plantingDate = plantingDate;
        c.expectedHarvestDate = expectedHarvestDate;
        c.seedInventoryItemId = seedInventoryItemId;
        c.seedQuantity = seedQuantity;
        c.seedSource = seedSource;
        c.status = plantingDate != null ? "PLANTED" : "PLANNED";
        c.notes = notes;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String variety, String cycleName, BigDecimal areaPlantedHectares,
                        LocalDate expectedHarvestDate, String notes) {
        this.variety = variety;
        this.cycleName = cycleName;
        if (areaPlantedHectares != null && areaPlantedHectares.signum() > 0) this.areaPlantedHectares = areaPlantedHectares;
        this.expectedHarvestDate = expectedHarvestDate;
        this.notes = notes;
    }

    public void recordPlanting(LocalDate plantingDate, UUID seedInventoryItemId, BigDecimal seedQuantity, String seedSource) {
        if (plantingDate == null) throw new IllegalArgumentException("plantingDate is required");
        if (!"PLANNED".equals(status)) throw new IllegalStateException("cannot record planting for a cycle already in status " + status);
        this.plantingDate = plantingDate;
        this.seedInventoryItemId = seedInventoryItemId;
        this.seedQuantity = seedQuantity;
        this.seedSource = seedSource;
        this.status = "PLANTED";
    }

    public void markGrowing() {
        if (!"PLANTED".equals(status)) throw new IllegalStateException("cannot mark growing from status " + status);
        this.status = "GROWING";
    }

    public void startHarvest() {
        if (!"GROWING".equals(status) && !"PLANTED".equals(status)) {
            throw new IllegalStateException("cannot start harvest from status " + status);
        }
        this.status = "HARVESTING";
    }

    public void completeHarvest() {
        if (!"HARVESTING".equals(status)) throw new IllegalStateException("cannot complete harvest from status " + status);
        this.status = "HARVESTED";
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        if (reason != null && !reason.isBlank()) this.notes = (this.notes != null ? this.notes + " | " : "") + "Failed: " + reason;
    }

    public void abandon(String reason) {
        this.status = "ABANDONED";
        if (reason != null && !reason.isBlank()) this.notes = (this.notes != null ? this.notes + " | " : "") + "Abandoned: " + reason;
    }

    public void softDelete() { this.deletedAt = Instant.now(); }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
