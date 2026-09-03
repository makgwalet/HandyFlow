package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// ── PosTransactionItem ────────────────────────────────────────────────────────
@Entity
@Table(name = "pos_transaction_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosTransactionItem {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "transaction_id",   nullable = false) private UUID       transactionId;
    @Column(name = "tenant_id",        nullable = false) private UUID       tenantId;
    @Column(name = "catalogue_item_id")                  private UUID       catalogueItemId;
    @Column(name = "item_name",        nullable = false) private String     itemName;
    private String     sku;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal qty;
    @Column(name = "unit_price",    nullable = false, precision = 15, scale = 2) private BigDecimal unitPrice;
    @Column(name = "vat_rate",      nullable = false, precision = 5,  scale = 2) private BigDecimal vatRate      = BigDecimal.valueOf(15);
    @Column(name = "vat_amount",    nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount    = BigDecimal.ZERO;
    @Column(name = "discount_pct",  nullable = false, precision = 5,  scale = 2) private BigDecimal discountPct  = BigDecimal.ZERO;
    @Column(name = "discount_amount",nullable = false, precision = 15, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "line_total",    nullable = false, precision = 15, scale = 2) private BigDecimal lineTotal;

    public static PosTransactionItem create(UUID transactionId, UUID tenantId,
                                             UUID catalogueItemId, String itemName, String sku,
                                             BigDecimal qty, BigDecimal unitPrice,
                                             BigDecimal vatRate, BigDecimal discountPct) {
        PosTransactionItem i = new PosTransactionItem();
        i.transactionId    = transactionId;
        i.tenantId         = tenantId;
        i.catalogueItemId  = catalogueItemId;
        i.itemName         = itemName;
        i.sku              = sku;
        i.qty              = qty;
        i.unitPrice        = unitPrice;
        // FIX (VAT consolidation pass): both real callers in PosService
        // (processSale() via resolveVatRate(), and the refund path via
        // orig.getVatRate()) always resolve a concrete non-null vatRate
        // before reaching here — confirmed by reading both call sites —
        // so this fallback is not reachable through the real
        // application flow. Left as a defensive default rather than
        // wired to VatRateProvider directly: a domain entity's static
        // factory shouldn't reach into Spring-managed config, matching
        // the same convention already established for CatalogueItem's
        // own equivalent fallback.
        i.vatRate          = vatRate != null ? vatRate : BigDecimal.valueOf(15);
        i.discountPct      = discountPct != null ? discountPct : BigDecimal.ZERO;
        // Calculate line total
        BigDecimal lineSubtotal = unitPrice.multiply(qty);
        i.discountAmount   = lineSubtotal.multiply(i.discountPct)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal afterDiscount = lineSubtotal.subtract(i.discountAmount);
        i.vatAmount        = afterDiscount.multiply(i.vatRate)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        i.lineTotal        = afterDiscount.add(i.vatAmount);
        return i;
    }
}
