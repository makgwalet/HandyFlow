package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** What arrived against one purchase order line, at the unit cost actually booked. */
@Entity
@Table(name = "facility_goods_receipt_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityGoodsReceiptLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "po_line_id", nullable = false)
    private UUID poLineId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    static FacilityGoodsReceiptLine of(TenantId tenantId, FacilityGoodsReceipt.LineSpec s) {
        if (s.quantity() == null || s.quantity().signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (s.unitCost() == null || s.unitCost().signum() < 0) throw new IllegalArgumentException("unit cost must not be negative");
        FacilityGoodsReceiptLine l = new FacilityGoodsReceiptLine();
        l.tenantId = tenantId;
        l.poLineId = s.poLineId();
        l.itemId = s.itemId();
        l.description = s.description();
        l.quantity = s.quantity();
        l.unitCost = s.unitCost();
        return l;
    }

    public BigDecimal lineValue() { return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP); }
}
