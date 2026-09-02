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
 * A weight/growth measurement, against either one {@link AgAnimal} or one
 * {@link AgGroup} (sampled/average weight) — never both, never neither.
 * Append-only history, matching {@code earthmoving.OperatorLog}'s own
 * convention (no {@code updatedAt}/{@code version} — a mis-recorded weight
 * is corrected by recording a new one, not by editing history).
 */
@Entity
@Table(name = "ag_weight_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgWeightRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    /** Set only for a group record sampled from a subset of the batch rather than every animal. */
    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_by_name")
    private String recordedByName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgWeightRecord create(TenantId tenantId, UUID animalId, UUID groupId, LocalDate recordedDate,
                                         BigDecimal weightKg, Integer sampleSize,
                                         UUID recordedBy, String recordedByName, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgTrackingTarget.requireExactlyOne(animalId, groupId);
        if (recordedDate == null) throw new IllegalArgumentException("recordedDate is required");
        if (weightKg == null || weightKg.signum() <= 0) throw new IllegalArgumentException("weightKg must be positive");

        AgWeightRecord w = new AgWeightRecord();
        w.tenantId = tenantId;
        w.animalId = animalId;
        w.groupId = groupId;
        w.recordedDate = recordedDate;
        w.weightKg = weightKg;
        w.sampleSize = sampleSize;
        w.recordedBy = recordedBy;
        w.recordedByName = recordedByName;
        w.notes = notes;
        w.createdAt = Instant.now();
        return w;
    }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }
}
