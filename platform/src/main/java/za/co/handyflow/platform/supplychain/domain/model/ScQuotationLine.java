package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "sc_quotation_lines")
@Getter
@NoArgsConstructor
public class ScQuotationLine {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "quotation_id", nullable = false) UUID quotationId;
    @Column(name = "rfq_line_id", nullable = false) UUID rfqLineId;
    @Column(name = "no_bid", nullable = false) boolean noBid;
    @Column(name = "qty_offered", precision = 12, scale = 3) BigDecimal qtyOffered;
    @Column(name = "unit_price", precision = 15, scale = 2) BigDecimal unitPrice;
    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2) BigDecimal discountPct = BigDecimal.ZERO;
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2) BigDecimal vatRate;
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2) BigDecimal lineTotal = BigDecimal.ZERO;

    public static ScQuotationLine priced(UUID tenantId, UUID quotationId, UUID rfqLineId, BigDecimal qty,
                                         BigDecimal unitPrice, BigDecimal discountPct, BigDecimal vatRate) {
        ScQuotationLine l = new ScQuotationLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.quotationId = quotationId;
        l.rfqLineId = rfqLineId;
        l.noBid = false;
        l.qtyOffered = qty;
        l.unitPrice = unitPrice;
        l.discountPct = discountPct != null ? discountPct : BigDecimal.ZERO;
        l.vatRate = vatRate;
        l.lineTotal = l.netUnitPrice().multiply(qty).setScale(2, RoundingMode.HALF_UP);
        return l;
    }

    public static ScQuotationLine noBid(UUID tenantId, UUID quotationId, UUID rfqLineId, BigDecimal vatRate) {
        ScQuotationLine l = new ScQuotationLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.quotationId = quotationId;
        l.rfqLineId = rfqLineId;
        l.noBid = true;
        l.vatRate = vatRate;
        return l;
    }

    /** Unit price after the supplier's discount, 2dp — the price a PO line will carry. */
    public BigDecimal netUnitPrice() {
        if (unitPrice == null) return BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.subtract(discountPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return unitPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal vatAmount() {
        return lineTotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}