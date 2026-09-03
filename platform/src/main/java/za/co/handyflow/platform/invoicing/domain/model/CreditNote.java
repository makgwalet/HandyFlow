package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * FIX: "no credit note PDF" gap — TenantSequenceService's own doc comment
 * already anticipated a "CREDIT_NOTE" sequence ("in future 'CREDIT_NOTE'
 * or 'RECEIPT'"); this is that missing piece.
 * <p>
 * Deliberately does NOT mutate Invoice.total/amountPaid/status. Those
 * represent what was originally invoiced and paid; a credit note is a
 * separate accounting document that nets against them in reporting, not a
 * rewrite of the original invoice — the same reasoning ReceiptPdfService's
 * own doc comment gives for why a receipt is its own artifact rather than
 * a restated invoice. Computing "net amount actually owed" (invoice.total
 * − invoice.amountPaid − sum of credit notes against it) is left to
 * callers/reporting, not baked into Invoice itself, to avoid touching
 * Invoice's existing payment/status logic that other flows already depend
 * on.
 */
@Entity
@Table(name = "credit_notes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CreditNote {

    @Id
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "credit_note_number", nullable = false)
    private String creditNoteNumber;

    private String reason;
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private String currency = "ZAR";

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CreditNote create(TenantId tenantId, UUID invoiceId, String creditNoteNumber,
                                    String reason, String description,
                                    BigDecimal amountExVat, BigDecimal vatRate, String currency) {
        CreditNote cn = new CreditNote();
        cn.id = UUID.randomUUID();
        cn.tenantId = tenantId;
        cn.invoiceId = invoiceId;
        cn.creditNoteNumber = creditNoteNumber;
        cn.reason = reason;
        cn.description = description;
        cn.subtotal = amountExVat.setScale(2, RoundingMode.HALF_UP);
        // FIX (VAT sweep, module 2): CreditNoteService is this factory's
        // only real caller (confirmed by search) and now always resolves
        // a concrete default via VatRateProvider before calling here, so
        // this fallback is no longer reachable through the actual
        // application flow — left in place as a defensive backstop
        // rather than wired to VatRateProvider directly, since a domain
        // entity's static factory shouldn't reach into Spring-managed
        // config (matches the convention already established for
        // CatalogueItem's/PosTransactionItem's own equivalent fallbacks).
        BigDecimal rate = vatRate != null ? vatRate : new BigDecimal("15.00");
        cn.vatTotal = cn.subtotal.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        cn.total = cn.subtotal.add(cn.vatTotal);
        cn.currency = currency != null ? currency : "ZAR";
        cn.issuedAt = Instant.now();
        cn.createdAt = Instant.now();
        return cn;
    }
}