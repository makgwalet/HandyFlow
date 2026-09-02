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
 * A feed/ration consumption record against either one {@link AgAnimal} or
 * one {@link AgGroup}. {@code inventoryItemId} is nullable — a farm can
 * log feed given without tracking it against a formal
 * {@link AgInventoryItem} (e.g. home-grown roughage never purchased or
 * stocked); when it IS set, the application service issues a matching
 * {@link AgStockMovement} (ISSUE) against that item, and this record's
 * {@code feedType}/{@code costPerKg} become a snapshot of that item's
 * name/cost at the time rather than a live reference — the same
 * snapshot-not-live-join pattern {@code TrainingEnrollment} uses for an
 * employee's name via {@code HrFacade}. Append-only history.
 */
@Entity
@Table(name = "ag_feed_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgFeedRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "feed_date", nullable = false)
    private LocalDate feedDate;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "feed_type", nullable = false)
    private String feedType;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "cost_per_kg", precision = 12, scale = 4)
    private BigDecimal costPerKg;

    @Column(name = "total_cost", precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgFeedRecord create(TenantId tenantId, UUID animalId, UUID groupId, LocalDate feedDate,
                                       UUID inventoryItemId, String feedType, BigDecimal quantityKg,
                                       BigDecimal costPerKg, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgTrackingTarget.requireExactlyOne(animalId, groupId);
        if (feedDate == null) throw new IllegalArgumentException("feedDate is required");
        if (feedType == null || feedType.isBlank()) throw new IllegalArgumentException("feedType is required");
        if (quantityKg == null || quantityKg.signum() <= 0) throw new IllegalArgumentException("quantityKg must be positive");

        AgFeedRecord f = new AgFeedRecord();
        f.tenantId = tenantId;
        f.animalId = animalId;
        f.groupId = groupId;
        f.feedDate = feedDate;
        f.inventoryItemId = inventoryItemId;
        f.feedType = feedType;
        f.quantityKg = quantityKg;
        f.costPerKg = costPerKg;
        f.totalCost = costPerKg != null ? costPerKg.multiply(quantityKg) : null;
        f.notes = notes;
        f.createdAt = Instant.now();
        return f;
    }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }
}
