package za.co.handyflow.platform.invoicing;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Finance patch F1 (2026-10-13): a credit note was saved, emailed and printed but never reached the general ledger, so
 * Accounts Receivable, Revenue and VAT output stayed overstated by every credit note issued. Published after the credit
 * note is saved, the same way as {@link InvoiceIssuedEvent}; posted by
 * accounting.application.internal.InvoicingAccountingEventHandler as the reverse of the invoice's issuance journal:
 * Debit Revenue (subtotal), Debit VAT output (vatTotal), Credit Accounts Receivable (total).
 */
public record CreditNoteIssuedEvent(
        TenantId tenantId,
        UUID creditNoteId,
        String creditNoteNumber,
        UUID invoiceId,
        String invoiceNumber,
        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        Instant occurredOn
) implements DomainEvent {

    public static CreditNoteIssuedEvent of(TenantId tenantId, UUID creditNoteId, String creditNoteNumber, UUID invoiceId,
                                           String invoiceNumber, BigDecimal subtotal, BigDecimal vatTotal, BigDecimal total) {
        return new CreditNoteIssuedEvent(tenantId, creditNoteId, creditNoteNumber, invoiceId, invoiceNumber,
                subtotal, vatTotal, total, Instant.now());
    }
}
