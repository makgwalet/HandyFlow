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
 * A death/loss record against either one {@link AgAnimal} (always
 * {@code countLost = 1}) or a partial loss from an {@link AgGroup}
 * ({@code countLost} > 1 for e.g. a disease event across a flock).
 * Append-only history. The application service applying this record is
 * responsible for calling {@code AgAnimal.changeStatus("DECEASED")} or
 * {@code AgGroup.reduceCount(countLost)} — this entity only records the
 * fact of the loss, it does not mutate the animal/group itself, keeping
 * the same separation of concerns {@code MaintenanceRecord} has from
 * {@code EarthAsset.currentHours}.
 */
@Entity
@Table(name = "ag_mortality_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgMortalityRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "mortality_date", nullable = false)
    private LocalDate mortalityDate;

    @Column(name = "count_lost", nullable = false)
    private Integer countLost;

    /** DISEASE | PREDATOR | ACCIDENT | UNKNOWN | CULLED | OTHER */
    @Column(name = "cause_category", nullable = false)
    private String causeCategory;

    @Column(name = "cause_detail")
    private String causeDetail;

    @Column(name = "estimated_value_loss", precision = 12, scale = 2)
    private BigDecimal estimatedValueLoss;

    @Column(name = "reported_by")
    private UUID reportedBy;

    @Column(name = "reported_by_name")
    private String reportedByName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgMortalityRecord create(TenantId tenantId, UUID animalId, UUID groupId, LocalDate mortalityDate,
                                            Integer countLost, String causeCategory, String causeDetail,
                                            BigDecimal estimatedValueLoss, UUID reportedBy, String reportedByName,
                                            String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgTrackingTarget.requireExactlyOne(animalId, groupId);
        if (mortalityDate == null) throw new IllegalArgumentException("mortalityDate is required");
        if (countLost == null || countLost <= 0) throw new IllegalArgumentException("countLost must be positive");
        if (animalId != null && countLost != 1) throw new IllegalArgumentException("countLost must be 1 for an individually-tracked animal");
        if (causeCategory == null || causeCategory.isBlank()) throw new IllegalArgumentException("causeCategory is required");

        AgMortalityRecord m = new AgMortalityRecord();
        m.tenantId = tenantId;
        m.animalId = animalId;
        m.groupId = groupId;
        m.mortalityDate = mortalityDate;
        m.countLost = countLost;
        m.causeCategory = causeCategory;
        m.causeDetail = causeDetail;
        m.estimatedValueLoss = estimatedValueLoss;
        m.reportedBy = reportedBy;
        m.reportedByName = reportedByName;
        m.notes = notes;
        m.createdAt = Instant.now();
        return m;
    }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }
}
