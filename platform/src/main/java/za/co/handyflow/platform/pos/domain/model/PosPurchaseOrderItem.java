package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pos_purchase_order_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosPurchaseOrderItem {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "purchase_order_id", nullable = false) private UUID       purchaseOrderId;
    @Column(name = "tenant_id",         nullable = false) private UUID       tenantId;
    @Column(name = "catalogue_item_id")                   private UUID       catalogueItemId;
    @Column(name = "item_name",         nullable = false) private String     itemName;
    @Column(name = "qty_ordered",       nullable = false, precision = 12, scale = 3) private BigDecimal qtyOrdered;
    @Column(name = "qty_received",      nullable = false, precision = 12, scale = 3) private BigDecimal qtyReceived = BigDecimal.ZERO;
    @Column(name = "unit_cost",         nullable = false, precision = 15, scale = 2) private BigDecimal unitCost;
    @Column(name = "vat_rate",          nullable = false, precision = 5,  scale = 2) private BigDecimal vatRate = BigDecimal.valueOf(15);
    @Column(name = "line_total",        nullable = false, precision = 15, scale = 2) private BigDecimal lineTotal;

    public static PosPurchaseOrderItem create(UUID purchaseOrderId, UUID tenantId,
                                               UUID catalogueItemId, String itemName,
                                               BigDecimal qtyOrdered, BigDecimal unitCost,
                                               BigDecimal vatRate) {
        PosPurchaseOrderItem i = new PosPurchaseOrderItem();
        i.purchaseOrderId  = purchaseOrderId;
        i.tenantId         = tenantId;
        i.catalogueItemId  = catalogueItemId;
        i.itemName         = itemName;
        i.qtyOrdered       = qtyOrdered;
        i.unitCost         = unitCost;
        // FIX (VAT consolidation pass): this fallback WAS genuinely
        // reachable, unlike CatalogueItem's/PosTransactionItem's own
        // equivalent defaults — PosService.createPurchaseOrder() now
        // resolves a concrete default via VatRateProvider before
        // calling here (line.vatRate() is nullable at the DTO level
        // with no validation constraining it, confirmed directly
        // against CreatePurchaseOrderRequest, and previously flowed
        // straight through to this exact line unresolved). Left in
        // place as a defensive backstop for any other future caller,
        // now genuinely unreachable via the real application flow —
        // not wired to VatRateProvider directly since a domain entity's
        // static factory shouldn't reach into Spring-managed config.
        i.vatRate          = vatRate != null ? vatRate : BigDecimal.valueOf(15);
        BigDecimal sub     = unitCost.multiply(qtyOrdered);
        BigDecimal vat     = sub.multiply(i.vatRate)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        i.lineTotal        = sub.add(vat);
        return i;
    }

    public void receiveQty(BigDecimal qty) {
        this.qtyReceived = this.qtyReceived.add(qty);
    }

    public boolean isFullyReceived() {
        return qtyReceived.compareTo(qtyOrdered) >= 0;
    }
}
