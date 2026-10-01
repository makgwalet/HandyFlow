package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * One row of the Agriculture cost ledger (ADR-001, W1): a cost allocated to a single production target (crop cycle, group,
 * animal or enterprise). Only the NEW direct-cost categories live here; feed, health, inputs, seed and animal purchases stay in
 * their own tables, so nothing is counted twice.
 * <p>
 * Append-only: a correction is a REVERSAL row (negative amount) and the original becomes REVERSED, so net cost is a plain sum.
 * Rates that belong to another module (HR pay, Fleet equipment, Fuel) are snapshotted into quantity/unit/rate/amount here.
 */
@Entity
@Table(name = "ag_cost_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgCostEntry {

    public static final Set<String> CATEGORIES = Set.of("LABOUR", "EQUIPMENT", "FUEL", "OTHER_DIRECT");
    public static final String ACTIVE = "ACTIVE";
    public static final String REVERSED = "REVERSED";
    public static final String REVERSAL = "REVERSAL";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    private String category;                // LABOUR | EQUIPMENT | FUEL | OTHER_DIRECT

    @Column(nullable = false)
    private String description;

    @Column(name = "source_type", nullable = false)
    private String sourceType;              // MANUAL now; HR_LABOUR, FLEET_USAGE, FUEL_DISPATCH as those integrations arrive

    @Column(name = "source_ref")
    private UUID sourceRef;                 // the record in the owning module this was costed from

    @Column(name = "target_type", nullable = false)
    private String targetType;              // CROP_CYCLE | GROUP | ANIMAL | ENTERPRISE

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    private String unit;

    @Column(precision = 14, scale = 4)
    private BigDecimal rate;                // the rate used, snapshotted

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;              // negative on a reversal row

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;          // this row's share of the whole cost (100 when it was not split)

    @Column(name = "allocation_group_id", nullable = false)
    private UUID allocationGroupId;         // the rows of one split cost share this

    @Column(name = "reverses_entry_id")
    private UUID reversesEntryId;

    @Column(nullable = false)
    private String status = ACTIVE;         // ACTIVE | REVERSED | REVERSAL

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgCostEntry create(TenantId tenantId, UUID farmId, LocalDate entryDate, String category, String description,
                                     String sourceType, UUID sourceRef, String targetType, UUID targetId,
                                     BigDecimal quantity, String unit, BigDecimal rate, BigDecimal amount, BigDecimal percentage,
                                     UUID allocationGroupId, String notes, UUID createdBy) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (entryDate == null) throw new IllegalArgumentException("entryDate is required");
        if (category == null || !CATEGORIES.contains(category)) throw new IllegalArgumentException("category must be one of " + CATEGORIES);
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (description.length() > 255) throw new IllegalArgumentException("description must be at most 255 characters");
        if (sourceType == null || sourceType.isBlank()) throw new IllegalArgumentException("sourceType is required");
        if (targetType == null || !AgCostAllocation.TARGET_TYPES.contains(targetType)) throw new IllegalArgumentException("targetType must be one of " + AgCostAllocation.TARGET_TYPES);
        if (targetId == null) throw new IllegalArgumentException("targetId is required");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException("percentage must be greater than 0 and at most 100");
        if (allocationGroupId == null) throw new IllegalArgumentException("allocationGroupId is required");
        if (quantity != null && quantity.signum() < 0) throw new IllegalArgumentException("quantity must not be negative");

        AgCostEntry e = new AgCostEntry();
        e.tenantId = tenantId;
        e.farmId = farmId;
        e.entryDate = entryDate;
        e.category = category;
        e.description = description.trim();
        e.sourceType = sourceType;
        e.sourceRef = sourceRef;
        e.targetType = targetType;
        e.targetId = targetId;
        e.quantity = quantity;
        e.unit = unit == null || unit.isBlank() ? null : unit.trim();
        e.rate = rate;
        e.amount = amount;
        e.percentage = percentage;
        e.allocationGroupId = allocationGroupId;
        e.notes = notes;
        e.createdBy = createdBy;
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        return e;
    }

    /**
     * Reverses this row: it becomes REVERSED and the returned row (negative amount, same date, target and allocation group) is the
     * REVERSAL that nets it out. The reversal keeps the ORIGINAL entry date so the correction lands in the same period as the cost it
     * corrects; {@code createdAt} records when it was actually done.
     */
    public AgCostEntry reverse(String reason, UUID reversedBy) {
        if (!ACTIVE.equals(status)) throw new IllegalStateException("cost entry is already " + status);
        this.status = REVERSED;
        this.updatedAt = Instant.now();

        AgCostEntry r = new AgCostEntry();
        r.tenantId = tenantId;
        r.farmId = farmId;
        r.entryDate = entryDate;
        r.category = category;
        String text = "Reversal: " + description;
        r.description = text.length() > 255 ? text.substring(0, 255) : text;
        r.sourceType = sourceType;
        r.sourceRef = sourceRef;
        r.targetType = targetType;
        r.targetId = targetId;
        r.quantity = quantity;
        r.unit = unit;
        r.rate = rate;
        r.amount = amount.negate();
        r.percentage = percentage;
        r.allocationGroupId = allocationGroupId;
        r.reversesEntryId = id;
        r.status = REVERSAL;
        r.notes = reason == null || reason.isBlank() ? null : reason.trim();
        r.createdBy = reversedBy;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
