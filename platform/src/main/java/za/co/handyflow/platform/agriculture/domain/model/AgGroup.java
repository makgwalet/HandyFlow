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
 * A batch/flock/herd tracked as a single unit — the group-tracking half of
 * the central individual-vs-group design decision (see {@link AgAnimal}'s
 * own Javadoc). A broiler farm's 15,000-bird batch is one {@code AgGroup}
 * row; {@code currentCount} is decremented by {@link AgMortalityRecord}s
 * and partial {@link AgMovementRecord}s against this group, not recomputed
 * by summing history on every read — the same "denormalized current state,
 * append-only history for the trail" shape {@code EarthAsset.currentHours}
 * uses against {@code MaintenanceRecord}.
 */
@Entity
@Table(name = "ag_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgGroup {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "production_area_id")
    private UUID productionAreaId;

    @Column(name = "enterprise_id")
    private UUID enterpriseId;

    @Column(name = "species_id", nullable = false)
    private UUID speciesId;

    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    private String breed;

    @Column(name = "initial_count", nullable = false)
    private Integer initialCount;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount;

    @Column(name = "average_weight_kg", precision = 10, scale = 2)
    private BigDecimal averageWeightKg;

    @Column(name = "origin_date", nullable = false)
    private LocalDate originDate;

    @Column(name = "acquisition_type", nullable = false)
    private String acquisitionType; // BORN_ON_FARM | PURCHASED | TRANSFERRED_IN

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | CLOSED

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

    public static AgGroup create(TenantId tenantId, UUID farmId, UUID productionAreaId, UUID enterpriseId,
                                  UUID speciesId, String batchNumber, String breed, Integer initialCount,
                                  LocalDate originDate, String acquisitionType) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (speciesId == null) throw new IllegalArgumentException("speciesId is required");
        if (batchNumber == null || batchNumber.isBlank()) throw new IllegalArgumentException("batchNumber is required");
        if (initialCount == null || initialCount < 0) throw new IllegalArgumentException("initialCount must be >= 0");
        if (originDate == null) throw new IllegalArgumentException("originDate is required");
        if (acquisitionType == null || acquisitionType.isBlank()) throw new IllegalArgumentException("acquisitionType is required");

        AgGroup g = new AgGroup();
        g.tenantId = tenantId;
        g.farmId = farmId;
        g.productionAreaId = productionAreaId;
        g.enterpriseId = enterpriseId;
        g.speciesId = speciesId;
        g.batchNumber = batchNumber;
        g.breed = breed;
        g.initialCount = initialCount;
        g.currentCount = initialCount;
        g.originDate = originDate;
        g.acquisitionType = acquisitionType;
        g.createdAt = Instant.now();
        g.updatedAt = Instant.now();
        return g;
    }

    public void update(UUID productionAreaId, UUID enterpriseId, String breed, String notes) {
        this.productionAreaId = productionAreaId;
        this.enterpriseId = enterpriseId;
        this.breed = breed;
        this.notes = notes;
    }

    public void recordAverageWeight(BigDecimal averageWeightKg) {
        this.averageWeightKg = averageWeightKg;
    }

    public void moveTo(UUID productionAreaId) {
        this.productionAreaId = productionAreaId;
    }

    public void reduceCount(int by) {
        if (by <= 0) throw new IllegalArgumentException("reduction must be positive");
        if (by > currentCount) throw new IllegalStateException("cannot reduce group " + batchNumber + " by " + by + " — only " + currentCount + " remain");
        this.currentCount -= by;
        if (this.currentCount == 0) this.status = "CLOSED";
    }

    public void increaseCount(int by) {
        if (by <= 0) throw new IllegalArgumentException("increase must be positive");
        this.currentCount += by;
    }

    public void close() { this.status = "CLOSED"; }

    public void reopen() { this.status = "ACTIVE"; }

    public void softDelete() { this.deletedAt = Instant.now(); }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
