package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line in a stock adjustment batch.
 * qty_difference = qty_actual - qty_system  (positive = surplus, negative = deficit).
 */
@Entity
@Table(name = "pos_adjustment_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosStockAdjustmentItem {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "adjustment_id",  nullable = false) private UUID       adjustmentId;
    @Column(name = "stock_item_id",  nullable = false) private UUID       stockItemId;

    /** What the system recorded before the count */
    @Column(name = "qty_system", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtySystem;

    /** What was physically counted */
    @Column(name = "qty_actual", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyActual;

    /** qtyActual - qtySystem */
    @Column(name = "qty_difference", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyDifference;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PosStockAdjustmentItem create(UUID adjustmentId, UUID stockItemId,
                                                BigDecimal qtySystem, BigDecimal qtyActual) {
        PosStockAdjustmentItem item = new PosStockAdjustmentItem();
        item.adjustmentId   = adjustmentId;
        item.stockItemId    = stockItemId;
        item.qtySystem      = qtySystem;
        item.qtyActual      = qtyActual;
        item.qtyDifference  = qtyActual.subtract(qtySystem);
        return item;
    }
}
