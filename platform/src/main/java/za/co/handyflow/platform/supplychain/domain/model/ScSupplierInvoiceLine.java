package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A line on a supplier invoice. Totals are always computed server-side from
 * quantity x unitPrice (never trusted from the client), using the same
 * HALF_UP / 2dp rule as ScPoLine so PO and invoice lines compare like-for-like.
 */
@Entity
@Table(name = "sc_supplier_invoice_lines")
@Getter
@NoArgsConstructor
public class ScSupplierInvoiceLine {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "supplier_invoice_id", nullable = false) UUID supplierInvoiceId;
    @Column(name = "line_number", nullable = false) int lineNumber;
    @Column(name = "po_line_id") UUID poLineId;
    @Column(name = "item_name", nullable = false, length = 255) String itemName;
    @Column(nullable = false, precision = 12, scale = 3) BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2) BigDecimal unitPrice;
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2) BigDecimal vatRate;
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2) BigDecimal lineTotal;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2) BigDecimal vatAmount;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScSupplierInvoiceLine create(UUID tenantId, UUID invoiceId, int lineNumber,
                                               UUID poLineId, String itemName,
                                               BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {
        ScSupplierInvoiceLine l = new ScSupplierInvoiceLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.supplierInvoiceId = invoiceId;
        l.lineNumber = lineNumber;
        l.poLineId = poLineId;
        l.itemName = itemName;
        l.quantity = quantity;
        l.unitPrice = unitPrice;
        l.vatRate = vatRate;
        l.lineTotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        l.vatAmount = l.lineTotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        l.createdAt = Instant.now();
        return l;
    }
}
