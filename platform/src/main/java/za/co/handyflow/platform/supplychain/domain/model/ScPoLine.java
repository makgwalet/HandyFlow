package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_po_lines")
@Getter
@NoArgsConstructor
public class ScPoLine {

    @Id UUID id;
    @Column(name = "purchase_order_id", nullable = false) UUID purchaseOrderId;
    @Column(name = "tenant_id",         nullable = false) UUID tenantId;
    @Column(name = "catalogue_item_id") UUID catalogueItemId;
    @Column(name = "item_name",         nullable = false) String itemName;
    @Column(name = "supplier_sku")      String supplierSku;
    @Column(name = "qty_ordered",       nullable = false, precision = 12, scale = 3) BigDecimal qtyOrdered;
    @Column(name = "qty_received",      nullable = false, precision = 12, scale = 3) BigDecimal qtyReceived   = BigDecimal.ZERO;
    @Column(name = "qty_invoiced",      nullable = false, precision = 12, scale = 3) BigDecimal qtyInvoiced   = BigDecimal.ZERO;
    @Column(name = "unit_cost",         nullable = false, precision = 15, scale = 2) BigDecimal unitCost;
    @Column(name = "vat_rate",          nullable = false, precision = 5,  scale = 2) BigDecimal vatRate       = BigDecimal.valueOf(15);
    @Column(name = "vat_amount",        nullable = false, precision = 15, scale = 2) BigDecimal vatAmount     = BigDecimal.ZERO;
    @Column(name = "line_total",        nullable = false, precision = 15, scale = 2) BigDecimal lineTotal     = BigDecimal.ZERO;
    @Column(name = "line_total_incl",   nullable = false, precision = 15, scale = 2) BigDecimal lineTotalIncl = BigDecimal.ZERO;
    @Column(name = "is_fully_received", nullable = false) boolean isFullyReceived = false;
    @Column(name = "expected_delivery") LocalDate expectedDelivery;
    String notes;

    public static ScPoLine create(UUID tenantId, UUID purchaseOrderId, UUID catalogueItemId,
                                  String itemName, String supplierSku,
                                  BigDecimal qtyOrdered, BigDecimal unitCost, BigDecimal vatRate) {
        ScPoLine l = new ScPoLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.purchaseOrderId = purchaseOrderId;
        l.catalogueItemId = catalogueItemId;
        l.itemName = itemName;
        l.supplierSku = supplierSku;
        l.qtyOrdered = qtyOrdered;
        l.unitCost = unitCost;
        l.vatRate = vatRate != null ? vatRate : BigDecimal.valueOf(15);
        l.recalculate();
        return l;
    }

    public void recordReceived(BigDecimal qty) {
        this.qtyReceived = this.qtyReceived.add(qty);
        this.isFullyReceived = this.qtyReceived.compareTo(this.qtyOrdered) >= 0;
    }

    private void recalculate() {
        this.lineTotal     = qtyOrdered.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        this.vatAmount     = lineTotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        this.lineTotalIncl = lineTotal.add(vatAmount);
    }
}
