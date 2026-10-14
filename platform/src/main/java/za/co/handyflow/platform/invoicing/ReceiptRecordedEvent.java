package za.co.handyflow.platform.invoicing;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money was received from a customer (finance patch F8, 2026-10-14). Replaces InvoicePaymentRecordedEvent for new
 * payments: one receipt may settle several invoices or sit on the customer's account, so the ledger posts the money once
 * per receipt (Dr the bank account, Cr Accounts Receivable), keyed on {@code receiptId}. How it is allocated between
 * invoices does not change the ledger.
 */
public record ReceiptRecordedEvent(
        TenantId tenantId,
        UUID receiptId,
        String receiptNumber,
        UUID customerId,
        BigDecimal amount,
        UUID bankAccountId,
        LocalDate receivedOn,
        String reference,
        String invoiceNumbers,
        Instant occurredOn
) implements DomainEvent {

    public static ReceiptRecordedEvent of(TenantId tenantId, UUID receiptId, String receiptNumber, UUID customerId,
                                          BigDecimal amount, UUID bankAccountId, LocalDate receivedOn, String reference,
                                          String invoiceNumbers) {
        return new ReceiptRecordedEvent(tenantId, receiptId, receiptNumber, customerId, amount, bankAccountId, receivedOn,
                reference, invoiceNumbers, Instant.now());
    }
}
