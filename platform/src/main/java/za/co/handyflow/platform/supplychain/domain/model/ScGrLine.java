package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_gr_lines")
@Getter
@NoArgsConstructor
public class ScGrLine {

    @Id UUID id;
    @Column(name = "goods_receipt_id", nullable = false) UUID goodsReceiptId;
    @Column(name = "po_line_id",       nullable = false) UUID poLineId;
    @Column(name = "tenant_id",        nullable = false) UUID tenantId;
    @Column(name = "catalogue_item_id") UUID catalogueItemId;
    @Column(name = "item_name",        nullable = false) String itemName;
    @Column(name = "qty_ordered",      nullable = false, precision = 12, scale = 3) BigDecimal qtyOrdered;
    @Column(name = "qty_received",     nullable = false, precision = 12, scale = 3) BigDecimal qtyReceived;
    @Column(name = "qty_rejected",     nullable = false, precision = 12, scale = 3) BigDecimal qtyRejected = BigDecimal.ZERO;
    @Column(name = "rejection_reason") String rejectionReason;
    @Column(name = "unit_cost",        nullable = false, precision = 15, scale = 2) BigDecimal unitCost;
    @Column(name = "lot_number",       length = 50) String lotNumber;
    @Column(name = "expiry_date")      LocalDate expiryDate;
    @Column(name = "serial_numbers")   String serialNumbers;
    @Column(nullable = false, length = 20) String condition = "GOOD";

    public static ScGrLine create(UUID tenantId, UUID goodsReceiptId, UUID poLineId,
                                  UUID catalogueItemId, String itemName,
                                  BigDecimal qtyOrdered, BigDecimal qtyReceived,
                                  BigDecimal unitCost, String condition) {
        ScGrLine l = new ScGrLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.goodsReceiptId = goodsReceiptId;
        l.poLineId = poLineId;
        l.catalogueItemId = catalogueItemId;
        l.itemName = itemName;
        l.qtyOrdered = qtyOrdered;
        l.qtyReceived = qtyReceived;
        l.unitCost = unitCost;
        l.condition = condition != null ? condition : "GOOD";
        return l;
    }
}
