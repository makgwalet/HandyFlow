package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sc_requisition_lines")
@Getter
@NoArgsConstructor
public class ScRequisitionLine {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "requisition_id", nullable = false) UUID requisitionId;
    @Column(name = "line_number", nullable = false) int lineNumber;
    @Column(name = "catalogue_item_id") UUID catalogueItemId;
    @Column(name = "item_name", nullable = false, length = 255) String itemName;
    @Column(length = 500) String description;
    @Column(name = "qty_requested", nullable = false, precision = 12, scale = 3) BigDecimal qtyRequested;
    @Column(name = "estimated_unit_cost", nullable = false, precision = 15, scale = 2) BigDecimal estimatedUnitCost;
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2) BigDecimal vatRate;
    @Column(name = "suggested_supplier_id") UUID suggestedSupplierId;
    @Column(name = "stock_snapshot", precision = 12, scale = 3) BigDecimal stockSnapshot;
    @Column(name = "on_order_snapshot", precision = 12, scale = 3) BigDecimal onOrderSnapshot;
    @Column(name = "already_requested_snapshot", precision = 12, scale = 3) BigDecimal alreadyRequestedSnapshot;

    public static ScRequisitionLine create(UUID tenantId, UUID requisitionId, int lineNumber, UUID catalogueItemId,
                                           String itemName, String description, BigDecimal qty, BigDecimal estCost,
                                           BigDecimal vatRate, UUID suggestedSupplierId) {
        ScRequisitionLine l = new ScRequisitionLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.requisitionId = requisitionId;
        l.lineNumber = lineNumber;
        l.catalogueItemId = catalogueItemId;
        l.itemName = itemName;
        l.description = description;
        l.qtyRequested = qty;
        l.estimatedUnitCost = estCost;
        l.vatRate = vatRate;
        l.suggestedSupplierId = suggestedSupplierId;
        return l;
    }

    /** What the approver saw at the moment of submission. */
    public void snapshot(BigDecimal stock, BigDecimal onOrder, BigDecimal alreadyRequested) {
        this.stockSnapshot = stock;
        this.onOrderSnapshot = onOrder;
        this.alreadyRequestedSnapshot = alreadyRequested;
    }
}
