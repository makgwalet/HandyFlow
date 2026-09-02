package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A business line within a farm — "Beef Cattle", "Dairy Herd", "Broiler
 * Batches" — the unit that Increment 1's cost/profitability reporting
 * (mirroring {@code FleetCostService}'s own cost-per-unit-of-output
 * pattern) rolls animals, groups, and their history records up to.
 * Nullable on {@code AgAnimal}/{@code AgGroup} — a farm can operate for a
 * while before its owner bothers to define enterprises, and every animal
 * doesn't strictly need one to be tracked.
 */
@Entity
@Table(name = "ag_enterprises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgEnterprise {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false)
    private String name;

    /** LIVESTOCK | CROP | POULTRY | AQUACULTURE | MIXED */
    @Column(name = "enterprise_type", nullable = false)
    private String enterpriseType;

    @Column(name = "species_focus")
    private String speciesFocus;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

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

    public static AgEnterprise create(TenantId tenantId, UUID farmId, String name, String enterpriseType,
                                       String speciesFocus, LocalDate startDate) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (enterpriseType == null || enterpriseType.isBlank()) throw new IllegalArgumentException("enterpriseType is required");

        AgEnterprise e = new AgEnterprise();
        e.tenantId = tenantId;
        e.farmId = farmId;
        e.name = name;
        e.enterpriseType = enterpriseType;
        e.speciesFocus = speciesFocus;
        e.startDate = startDate;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void update(String name, String speciesFocus, String notes) {
        if (name != null && !name.isBlank()) this.name = name;
        this.speciesFocus = speciesFocus;
        this.notes = notes;
    }

    public void deactivate() { this.status = "INACTIVE"; }

    public void reactivate() { this.status = "ACTIVE"; }

    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
