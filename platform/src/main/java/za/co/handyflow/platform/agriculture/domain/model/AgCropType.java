package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant-scoped crop catalogue entry (Maize, Wheat, Soybeans, Tomatoes,
 * Citrus, etc.) — the Crops sub-domain's direct structural counterpart to
 * {@link AgSpecies}, built the same way for the same reason: a farm's real
 * crop list is too open-ended for a fixed platform-wide enum, so this is
 * tenant-owned, soft-deletable master data, matching {@code TrainingCourse}'s
 * established catalogue shape.
 * <p>
 * No separate variety entity, for the same reason Increment 1 dropped a
 * separate {@code AgBreed} entity — variety is a plain string field on
 * {@link AgCropCycle} instead.
 */
@Entity
@Table(name = "ag_crop_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgCropType {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    /** FIELD_CROP | HORTICULTURE | ORCHARD | OTHER */
    @Column(nullable = false)
    private String category;

    @Column(name = "typical_growing_days")
    private Integer typicalGrowingDays;

    @Column(name = "default_unit_of_measure", nullable = false)
    private String defaultUnitOfMeasure = "kg";

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static AgCropType create(TenantId tenantId, String name, String category,
                                     Integer typicalGrowingDays, String defaultUnitOfMeasure) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");

        AgCropType c = new AgCropType();
        c.tenantId = tenantId;
        c.name = name;
        c.category = category;
        c.typicalGrowingDays = typicalGrowingDays;
        if (defaultUnitOfMeasure != null && !defaultUnitOfMeasure.isBlank()) c.defaultUnitOfMeasure = defaultUnitOfMeasure;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String name, Integer typicalGrowingDays, String defaultUnitOfMeasure) {
        if (name != null && !name.isBlank()) this.name = name;
        this.typicalGrowingDays = typicalGrowingDays;
        if (defaultUnitOfMeasure != null && !defaultUnitOfMeasure.isBlank()) this.defaultUnitOfMeasure = defaultUnitOfMeasure;
    }

    public void deactivate() { this.status = "INACTIVE"; }

    public void reactivate() { this.status = "ACTIVE"; }

    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
