package za.co.handyflow.platform.agriculture.domain.model;

import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.agriculture.domain.rules.AgBreedingRules;
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
@EntityListeners(AgAuditListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgBreedingRecord implements AgAudited {

    /** Who added this record and who last changed it (see {@link AgAuditListener}). Null on records from before it was kept. */
    @Embedded
    private AgAuditStamp audit = new AgAuditStamp();

    @Override public AgAuditStamp getAudit() { return audit; }
    @Override public void setAudit(AgAuditStamp audit) { this.audit = audit; }


    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    /** NATURAL | AI (artificial insemination) | EMBRYO_TRANSFER */
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
    private String outcome = "PENDING"; // PENDING | CONFIRMED_PREGNANT | NOT_PREGNANT | BIRTHED | STILLBORN | ABORTED | FAILED (see AgBreedingRules)

    /** Born alive. (Before stillbirths were tracked separately this held the total born.) */
    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "stillborn_count")
    private Integer stillbornCount;

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
        String type = AgBreedingRules.normaliseType(breedingType);
        AgBreedingRules.requireMatingDates(matingDate, expectedDueDate, AgDates.today());

        AgBreedingRecord b = new AgBreedingRecord();
        b.tenantId = tenantId;
        b.animalId = animalId;
        b.groupId = groupId;
        b.breedingType = type;
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
        AgBreedingRules.requireTransition(outcome, AgBreedingRules.CONFIRMED_PREGNANT);
        if (expectedDueDate != null) {
            AgBreedingRules.requireMatingDates(matingDate, expectedDueDate, AgDates.today());
            this.expectedDueDate = expectedDueDate;
        }
        this.outcome = AgBreedingRules.CONFIRMED_PREGNANT;
    }

    public void markNotPregnant() { moveTo(AgBreedingRules.NOT_PREGNANT); }

    /** live and stillborn may be null (zero); at least one animal must have been born. */
    public void recordBirth(LocalDate actualBirthDate, Integer live, Integer stillborn) {
        int[] counts = AgBreedingRules.requireBirthCounts(live, stillborn);
        String next = AgBreedingRules.outcomeForBirth(counts[0]);
        AgBreedingRules.requireTransition(outcome, next);
        AgBreedingRules.requireBirthDate(matingDate, actualBirthDate, AgDates.today());
        this.outcome = next;
        this.actualBirthDate = actualBirthDate;
        this.offspringCount = counts[0];
        this.stillbornCount = counts[1];
    }

    public void markAborted() { moveTo(AgBreedingRules.ABORTED); }

    public void markFailed() { moveTo(AgBreedingRules.FAILED); }

    private void moveTo(String next) {
        AgBreedingRules.requireTransition(outcome, next);
        this.outcome = next;
    }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
