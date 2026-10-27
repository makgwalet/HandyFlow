package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** A line on a purchase order. The description is a snapshot of the item's SKU and name when the order was raised. */
@Entity
@Table(name = "facility_purchase_order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityPurchaseOrderLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "ordered_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal orderedQty;

    @Column(name = "received_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    static FacilityPurchaseOrderLine of(TenantId tenantId, int lineNo, FacilityPurchaseOrder.LineSpec s) {
        if (s.quantity() == null || s.quantity().signum() <= 0) throw new IllegalArgumentException("line quantity must be positive");
        if (s.unitCost() == null || s.unitCost().signum() < 0) throw new IllegalArgumentException("line unit cost must not be negative");
        FacilityPurchaseOrderLine l = new FacilityPurchaseOrderLine();
        l.tenantId = tenantId;
        l.lineNo = lineNo;
        l.itemId = s.itemId();
        l.description = s.description();
        l.orderedQty = s.quantity();
        l.unitCost = s.unitCost().setScale(2, RoundingMode.HALF_UP);
        return l;
    }

    void receive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("received quantity must be positive");
        if (receivedQty.add(quantity).compareTo(orderedQty) > 0)
            throw new IllegalStateException("Cannot receive " + quantity.stripTrailingZeros().toPlainString() + " of " + description
                    + ": only " + outstanding().stripTrailingZeros().toPlainString() + " still outstanding");
        this.receivedQty = receivedQty.add(quantity);
    }

    public BigDecimal outstanding() { return orderedQty.subtract(receivedQty); }
    public boolean isComplete() { return receivedQty.compareTo(orderedQty) >= 0; }
    public BigDecimal lineTotal() { return orderedQty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP); }
}
