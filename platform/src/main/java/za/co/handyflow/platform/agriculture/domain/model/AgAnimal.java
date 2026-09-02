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
 * A single, individually-tracked animal. Used when a farm cares about this
 * specific animal's own history (a stud cow, a dairy cow, a breeding sow)
 * — as opposed to {@link AgGroup}, used when a farm tracks a batch/flock/
 * herd as one unit (e.g. 15,000 broilers). Every Livestock history entity
 * ({@link AgWeightRecord}, {@link AgHealthEvent}, {@link AgBreedingRecord},
 * {@link AgMovementRecord}, {@link AgMortalityRecord}, {@link AgFeedRecord})
 * references EITHER an animal OR a group, never both — see each entity's
 * own {@code create()} for the enforced invariant.
 * <p>
 * {@code sireId}/{@code damId} are self-references to other {@code AgAnimal}
 * rows (nullable — parentage is frequently unknown, especially for
 * purchased-in stock) enabling simple lineage tracing without a separate
 * pedigree entity in Increment 1.
 */
@Entity
@Table(name = "ag_animals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgAnimal {

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

    @Column(name = "tag_number", nullable = false)
    private String tagNumber;

    private String name;

    private String breed;

    @Column(nullable = false)
    private String sex; // MALE | FEMALE | CASTRATED

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "estimated_age", nullable = false)
    private boolean estimatedAge = false;

    @Column(name = "sire_id")
    private UUID sireId;

    @Column(name = "dam_id")
    private UUID damId;

    @Column(name = "acquisition_type", nullable = false)
    private String acquisitionType; // BORN_ON_FARM | PURCHASED | TRANSFERRED_IN

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_cost", precision = 12, scale = 2)
    private BigDecimal acquisitionCost;

    @Column(name = "current_weight_kg", precision = 10, scale = 2)
    private BigDecimal currentWeightKg;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | SOLD | DECEASED | CULLED | TRANSFERRED_OUT

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

    public static AgAnimal create(TenantId tenantId, UUID farmId, UUID productionAreaId, UUID enterpriseId,
                                   UUID speciesId, String tagNumber, String name, String breed, String sex,
                                   LocalDate dateOfBirth, boolean estimatedAge, UUID sireId, UUID damId,
                                   String acquisitionType, LocalDate acquisitionDate, BigDecimal acquisitionCost) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (speciesId == null) throw new IllegalArgumentException("speciesId is required");
        if (tagNumber == null || tagNumber.isBlank()) throw new IllegalArgumentException("tagNumber is required");
        if (sex == null || sex.isBlank()) throw new IllegalArgumentException("sex is required");
        if (acquisitionType == null || acquisitionType.isBlank()) throw new IllegalArgumentException("acquisitionType is required");
        if (acquisitionDate == null) throw new IllegalArgumentException("acquisitionDate is required");

        AgAnimal a = new AgAnimal();
        a.tenantId = tenantId;
        a.farmId = farmId;
        a.productionAreaId = productionAreaId;
        a.enterpriseId = enterpriseId;
        a.speciesId = speciesId;
        a.tagNumber = tagNumber;
        a.name = name;
        a.breed = breed;
        a.sex = sex;
        a.dateOfBirth = dateOfBirth;
        a.estimatedAge = estimatedAge;
        a.sireId = sireId;
        a.damId = damId;
        a.acquisitionType = acquisitionType;
        a.acquisitionDate = acquisitionDate;
        a.acquisitionCost = acquisitionCost;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(UUID productionAreaId, UUID enterpriseId, String name, String breed,
                        LocalDate dateOfBirth, boolean estimatedAge, UUID sireId, UUID damId, String notes) {
        this.productionAreaId = productionAreaId;
        this.enterpriseId = enterpriseId;
        this.name = name;
        this.breed = breed;
        this.dateOfBirth = dateOfBirth;
        this.estimatedAge = estimatedAge;
        this.sireId = sireId;
        this.damId = damId;
        this.notes = notes;
    }

    public void recordWeight(BigDecimal weightKg) {
        this.currentWeightKg = weightKg;
    }

    public void moveTo(UUID productionAreaId) {
        this.productionAreaId = productionAreaId;
    }

    public void changeStatus(String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        this.status = status;
    }

    public void softDelete() { this.deletedAt = Instant.now(); }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
