package za.co.handyflow.platform.invoicing;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FIX: backlog 1.6. See InvoiceIssuedEvent's own Javadoc for the full
 * architectural rationale (published here, not a direct AccountingFacade
 * call, to avoid a circular module dependency).
 * <p>
 * bankAccountId is NULLABLE — RecordPaymentRequest never captured which
 * bank account received the funds before this fix (paymentMethod is
 * explicitly commented "informational only," a free string like "EFT"/
 * "CASH", not a bank account reference). Added as a new OPTIONAL field
 * rather than guessing at a default bank account: a payment recorded
 * without one still updates the invoice's own amountPaid (unchanged,
 * existing behaviour), but the listener consuming this event cannot
 * post a directed "Debit Bank / Credit AR" journal without knowing
 * which bank account to debit — see
 * accounting.application.internal.InvoicingAccountingEventHandler for
 * how it handles that case (logs clearly, does not post, does not guess).
 */
public record InvoicePaymentRecordedEvent(
        TenantId tenantId,
        UUID invoiceId,
        String invoiceNumber,
        BigDecimal amountPaid,
        UUID bankAccountId,
        Instant occurredOn
) implements DomainEvent {

    public static InvoicePaymentRecordedEvent of(TenantId tenantId, UUID invoiceId, String invoiceNumber,
                                                 BigDecimal amountPaid, UUID bankAccountId) {
        return new InvoicePaymentRecordedEvent(tenantId, invoiceId, invoiceNumber,
                amountPaid, bankAccountId, Instant.now());
    }
}