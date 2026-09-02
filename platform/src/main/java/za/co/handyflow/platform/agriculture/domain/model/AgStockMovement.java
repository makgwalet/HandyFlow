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
 * A single inventory transaction against an {@link AgInventoryItem} —
 * receipt, issue, adjustment, or wastage. {@code quantity} is always
 * recorded positive; {@code movementType} determines the direction the
 * application service applies to {@code AgInventoryItem.currentQuantity}.
 * Append-only history. {@code referenceType}/{@code referenceId} link back
 * to whatever triggered the movement — most commonly an
 * {@link AgFeedRecord} auto-generating an ISSUE, but left generic so a
 * future manual purchase/goods-received entry point can populate it too.
 */
@Entity
@Table(name = "ag_stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgStockMovement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    /** RECEIPT | ISSUE | ADJUSTMENT | WASTAGE */
    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_by_name")
    private String performedByName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgStockMovement create(TenantId tenantId, UUID inventoryItemId, String movementType,
                                          LocalDate movementDate, BigDecimal quantity, BigDecimal unitCost,
                                          String referenceType, UUID referenceId,
                                          UUID performedBy, String performedByName, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (inventoryItemId == null) throw new IllegalArgumentException("inventoryItemId is required");
        if (movementType == null || movementType.isBlank()) throw new IllegalArgumentException("movementType is required");
        if (movementDate == null) throw new IllegalArgumentException("movementDate is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");

        AgStockMovement m = new AgStockMovement();
        m.tenantId = tenantId;
        m.inventoryItemId = inventoryItemId;
        m.movementType = movementType;
        m.movementDate = movementDate;
        m.quantity = quantity;
        m.unitCost = unitCost;
        m.totalCost = unitCost != null ? unitCost.multiply(quantity) : null;
        m.referenceType = referenceType;
        m.referenceId = referenceId;
        m.performedBy = performedBy;
        m.performedByName = performedByName;
        m.notes = notes;
        m.createdAt = Instant.now();
        return m;
    }
}
