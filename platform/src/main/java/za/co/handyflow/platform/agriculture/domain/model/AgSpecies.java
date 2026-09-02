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
 * A tenant-scoped species catalogue entry (Cattle, Sheep, Goat, Pig,
 * Chicken — Broiler, Chicken — Layer, etc.), matching the tenant-owned
 * catalogue shape {@code TrainingCourse} already established for this
 * codebase (soft-deletable master data, not a fixed platform-wide enum).
 * <p>
 * REVISION FROM THE DELIVERED ARCHITECTURE PLAN: the plan document listed
 * "AgSpecies / AgBreed" as two separate entities. Building it, a full
 * {@code AgBreed} catalogue entity was dropped in favour of a plain
 * {@code breed} string field directly on {@code AgAnimal}/{@code AgGroup}
 * — breed names vary too freely across species and regions for a
 * pre-populated, foreign-keyed catalogue to earn its own table in
 * Increment 1; free text captures 100% of what a farm actually records
 * without forcing a data-migration exercise onto every future breed
 * variant. Flagged in the status report as a further simplification.
 */
@Entity
@Table(name = "ag_species")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgSpecies {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    /** LIVESTOCK | POULTRY | AQUACULTURE — reporting grouping; only LIVESTOCK is populated by Increment 1's own UI. */
    @Column(nullable = false)
    private String category;

    @Column(name = "default_unit_of_measure", nullable = false)
    private String defaultUnitOfMeasure = "head";

    /** INDIVIDUAL | GROUP | BOTH — a UI hint for whether AgAnimal or AgGroup is the natural default for this species. */
    @Column(name = "tracking_mode", nullable = false)
    private String trackingMode = "BOTH";

    @Column(name = "gestation_days")
    private Integer gestationDays;

    @Column(name = "maturity_weight_kg", precision = 10, scale = 2)
    private BigDecimal maturityWeightKg;

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

    public static AgSpecies create(TenantId tenantId, String name, String category, String defaultUnitOfMeasure,
                                    String trackingMode, Integer gestationDays, BigDecimal maturityWeightKg) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");

        AgSpecies s = new AgSpecies();
        s.tenantId = tenantId;
        s.name = name;
        s.category = category;
        if (defaultUnitOfMeasure != null && !defaultUnitOfMeasure.isBlank()) s.defaultUnitOfMeasure = defaultUnitOfMeasure;
        if (trackingMode != null && !trackingMode.isBlank()) s.trackingMode = trackingMode;
        s.gestationDays = gestationDays;
        s.maturityWeightKg = maturityWeightKg;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String name, String defaultUnitOfMeasure, String trackingMode,
                        Integer gestationDays, BigDecimal maturityWeightKg) {
        if (name != null && !name.isBlank()) this.name = name;
        if (defaultUnitOfMeasure != null && !defaultUnitOfMeasure.isBlank()) this.defaultUnitOfMeasure = defaultUnitOfMeasure;
        if (trackingMode != null && !trackingMode.isBlank()) this.trackingMode = trackingMode;
        this.gestationDays = gestationDays;
        this.maturityWeightKg = maturityWeightKg;
    }

    public void deactivate() { this.status = "INACTIVE"; }

    public void reactivate() { this.status = "ACTIVE"; }

    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
