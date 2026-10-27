package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** One immutable line in the stock ledger. {@code quantity} is signed: positive adds stock, negative removes it. */
@Entity
@Table(name = "facility_stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityStockMovement {

    public static final Set<String> TYPES = Set.of("RECEIPT", "ISSUE", "RETURN", "ADJUSTMENT", "TRANSFER_OUT", "TRANSFER_IN");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "movement_type", nullable = false, length = 20)
    private String movementType;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "balance_after", nullable = false, precision = 14, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "cost_line_id")
    private UUID costLineId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(length = 300)
    private String reference;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FacilityStockMovement of(TenantId tenantId, UUID itemId, String type, BigDecimal signedQuantity,
                                           BigDecimal unitCost, BigDecimal balanceAfter, UUID workOrderId,
                                           UUID costLineId, UUID purchaseOrderId, String reference, UUID by) {
        return of(tenantId, itemId, null, type, signedQuantity, unitCost, balanceAfter, workOrderId, costLineId, purchaseOrderId, reference, by);
    }

    /** {@code balanceAfter} is the item's total on hand across all stores after this line. */
    public static FacilityStockMovement of(TenantId tenantId, UUID itemId, UUID storeId, String type, BigDecimal signedQuantity,
                                           BigDecimal unitCost, BigDecimal balanceAfter, UUID workOrderId,
                                           UUID costLineId, UUID purchaseOrderId, String reference, UUID by) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("movement type must be one of " + TYPES);
        FacilityStockMovement m = new FacilityStockMovement();
        m.tenantId = tenantId;
        m.itemId = itemId;
        m.storeId = storeId;
        m.movementType = type;
        m.quantity = signedQuantity;
        m.unitCost = unitCost;
        m.balanceAfter = balanceAfter;
        m.workOrderId = workOrderId;
        m.costLineId = costLineId;
        m.purchaseOrderId = purchaseOrderId;
        m.reference = reference == null || reference.isBlank() ? null
                : reference.trim().substring(0, Math.min(300, reference.trim().length()));
        m.createdByUserId = by;
        m.createdAt = Instant.now();
        return m;
    }
}
