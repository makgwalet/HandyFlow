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
 * A breeding/mating event against either one {@link AgAnimal} (the dam —
 * or, for a group-managed breeding pen, the {@link AgGroup}) and,
 * optionally, a known on-farm sire ({@code sireId}) or an external/unknown
 * one ({@code sireDescription}). Unlike the pure append-only history
 * entities, this one genuinely mutates over its own lifecycle (PENDING ->
 * CONFIRMED_PREGNANT -> BIRTHED, or ABORTED/FAILED/NOT_PREGNANT along the
 * way) so it carries {@code updatedAt}/{@code version} — matching
 * {@code earthmoving.MaintenanceRecord}'s fuller convention rather than
 * {@code OperatorLog}'s append-only one.
 */
@Entity
@Table(name = "ag_breeding_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgBreedingRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    /** NATURAL | AI (artificial insemination) */
    @Column(name = "breeding_type", nullable = false)
    private String breedingType;

    @Column(name = "mating_date", nullable = false)
    private LocalDate matingDate;

    /** Known on-farm sire, if applicable — nullable. */
    @Column(name = "sire_id")
    private UUID sireId;

    /** External or unrecorded sire — free text, nullable. */
    @Column(name = "sire_description")
    private String sireDescription;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "actual_birth_date")
    private LocalDate actualBirthDate;

    @Column(nullable = false)
    private String outcome = "PENDING"; // PENDING | CONFIRMED_PREGNANT | NOT_PREGNANT | BIRTHED | ABORTED | FAILED

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgBreedingRecord create(TenantId tenantId, UUID animalId, UUID groupId, String breedingType,
                                           LocalDate matingDate, UUID sireId, String sireDescription,
                                           LocalDate expectedDueDate, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgTrackingTarget.requireExactlyOne(animalId, groupId);
        if (breedingType == null || breedingType.isBlank()) throw new IllegalArgumentException("breedingType is required");
        if (matingDate == null) throw new IllegalArgumentException("matingDate is required");

        AgBreedingRecord b = new AgBreedingRecord();
        b.tenantId = tenantId;
        b.animalId = animalId;
        b.groupId = groupId;
        b.breedingType = breedingType;
        b.matingDate = matingDate;
        b.sireId = sireId;
        b.sireDescription = sireDescription;
        b.expectedDueDate = expectedDueDate;
        b.notes = notes;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        return b;
    }

    public void confirmPregnant(LocalDate expectedDueDate) {
        this.outcome = "CONFIRMED_PREGNANT";
        if (expectedDueDate != null) this.expectedDueDate = expectedDueDate;
    }

    public void markNotPregnant() { this.outcome = "NOT_PREGNANT"; }

    public void recordBirth(LocalDate actualBirthDate, Integer offspringCount) {
        if (actualBirthDate == null) throw new IllegalArgumentException("actualBirthDate is required");
        this.outcome = "BIRTHED";
        this.actualBirthDate = actualBirthDate;
        this.offspringCount = offspringCount;
    }

    public void markAborted() { this.outcome = "ABORTED"; }

    public void markFailed() { this.outcome = "FAILED"; }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
