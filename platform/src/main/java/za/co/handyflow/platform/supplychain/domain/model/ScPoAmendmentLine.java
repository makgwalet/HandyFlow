package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One requested line change. For UPDATE, null qty/cost/vat mean "leave as is". */
@Entity
@Table(name = "sc_po_amendment_lines")
@Getter
@NoArgsConstructor
public class ScPoAmendmentLine {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "amendment_id", nullable = false) UUID amendmentId;
    @Column(nullable = false, length = 10) String action;   // ADD | UPDATE | REMOVE
    @Column(name = "po_line_id") UUID poLineId;
    @Column(name = "catalogue_item_id") UUID catalogueItemId;
    @Column(name = "item_name", length = 255) String itemName;
    @Column(name = "supplier_sku", length = 100) String supplierSku;
    @Column(name = "qty_ordered", precision = 12, scale = 3) BigDecimal qtyOrdered;
    @Column(name = "unit_cost", precision = 15, scale = 2) BigDecimal unitCost;
    @Column(name = "vat_rate", precision = 5, scale = 2) BigDecimal vatRate;

    public static ScPoAmendmentLine create(UUID tenantId, UUID amendmentId, String action, UUID poLineId,
                                           UUID catalogueItemId, String itemName, String supplierSku,
                                           BigDecimal qtyOrdered, BigDecimal unitCost, BigDecimal vatRate) {
        ScPoAmendmentLine l = new ScPoAmendmentLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.amendmentId = amendmentId;
        l.action = action;
        l.poLineId = poLineId;
        l.catalogueItemId = catalogueItemId;
        l.itemName = itemName;
        l.supplierSku = supplierSku;
        l.qtyOrdered = qtyOrdered;
        l.unitCost = unitCost;
        l.vatRate = vatRate;
        return l;
    }
}
