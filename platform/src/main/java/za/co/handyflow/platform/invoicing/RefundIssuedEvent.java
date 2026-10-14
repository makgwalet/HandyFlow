package za.co.handyflow.platform.invoicing;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money paid back to a customer out of their credit (finance patch F9, 2026-10-14). Posted once per refund by
 * accounting.application.internal.InvoicingAccountingEventHandler: Dr Accounts Receivable, Cr the bank account it was paid from.
 */
public record RefundIssuedEvent(
        TenantId tenantId,
        UUID refundId,
        String refundNumber,
        String receiptNumber,
        UUID customerId,
        BigDecimal amount,
        UUID bankAccountId,
        LocalDate refundedOn,
        String reference,
        Instant occurredOn
) implements DomainEvent {

    public static RefundIssuedEvent of(TenantId tenantId, UUID refundId, String refundNumber, String receiptNumber, UUID customerId,
                                       BigDecimal amount, UUID bankAccountId, LocalDate refundedOn, String reference) {
        return new RefundIssuedEvent(tenantId, refundId, refundNumber, receiptNumber, customerId, amount, bankAccountId,
                refundedOn, reference, Instant.now());
    }
}
