package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A yield/harvest record against one {@link AgCropCycle} — this module's
 * {@code AgHarvest}-equivalent concept, named explicitly in this module's
 * own package-info.java as arriving with the Crops increment. Records what
 * was produced and its quality/quantity; it deliberately does NOT create a
 * sale or invoice — the architecture plan's §3 decision applies here
 * exactly as it did to Livestock's own movement records: the actual
 * commercial sale of a harvest belongs to the platform's {@code invoicing}/
 * {@code crm} modules, not duplicated in this one.
 * <p>
 * Multiple harvest records per cycle are expected and normal (a multi-pick
 * crop like tomatoes, or a phased grain harvest) — append-only history,
 * not a single denormalized total on {@link AgCropCycle}.
 */
@Entity
@Table(name = "ag_harvest_records")
@EntityListeners(AgAuditListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgHarvestRecord implements AgAudited {

    /** Who added this record (and, where it can be edited, who last changed it). See {@link AgAuditListener}. Null on records from before it was kept. */
    @Embedded
    private AgAuditStamp audit = new AgAuditStamp();

    @Override public AgAuditStamp getAudit() { return audit; }
    @Override public void setAudit(AgAuditStamp audit) { this.audit = audit; }


    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;

    @Column(name = "harvest_date", nullable = false)
    private LocalDate harvestDate;

    @Column(name = "quantity_harvested", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityHarvested;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    @Column(name = "quality_grade")
    private String qualityGrade;

    @Column(name = "moisture_content", precision = 5, scale = 2)
    private BigDecimal moistureContent;

    @Column(name = "storage_location")
    private String storageLocation;

    @Column(name = "harvested_by")
    private UUID harvestedBy;

    @Column(name = "harvested_by_name")
    private String harvestedByName;

    @Column(name = "labor_hours", precision = 8, scale = 2)
    private BigDecimal laborHours;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Why the harvest went ahead inside a pre-harvest interval; null when nothing was held. */
    @Column(name = "withdrawal_override_reason", columnDefinition = "TEXT")
    private String withdrawalOverrideReason;

    /** The day that pre-harvest interval would have run to. */
    @Column(name = "withdrawal_held_until")
    private LocalDate withdrawalHeldUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Keeps the reason for harvesting inside a pre-harvest interval, on the harvest record. */
    public void recordWithdrawalOverride(AgWithdrawalRules.Override override) {
        if (override == null) return;
        this.withdrawalOverrideReason = override.reason();
        this.withdrawalHeldUntil = override.heldUntil();
    }

    public static AgHarvestRecord create(TenantId tenantId, UUID cropCycleId, LocalDate harvestDate,
                                          BigDecimal quantityHarvested, String unitOfMeasure, String qualityGrade,
                                          BigDecimal moistureContent, String storageLocation, UUID harvestedBy,
                                          String harvestedByName, BigDecimal laborHours, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (cropCycleId == null) throw new IllegalArgumentException("cropCycleId is required");
        if (harvestDate == null) throw new IllegalArgumentException("harvestDate is required");
        if (quantityHarvested == null || quantityHarvested.signum() <= 0) throw new IllegalArgumentException("quantityHarvested must be positive");
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) throw new IllegalArgumentException("unitOfMeasure is required");

        AgHarvestRecord h = new AgHarvestRecord();
        h.tenantId = tenantId;
        h.cropCycleId = cropCycleId;
        h.harvestDate = harvestDate;
        h.quantityHarvested = quantityHarvested;
        h.unitOfMeasure = unitOfMeasure;
        h.qualityGrade = qualityGrade;
        h.moistureContent = moistureContent;
        h.storageLocation = storageLocation;
        h.harvestedBy = harvestedBy;
        h.harvestedByName = harvestedByName;
        h.laborHours = laborHours;
        h.notes = notes;
        h.createdAt = Instant.now();
        return h;
    }
}
