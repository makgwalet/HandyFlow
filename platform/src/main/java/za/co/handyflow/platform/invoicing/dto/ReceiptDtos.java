package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Finance patch F8: receipts, allocations and customer credit. */
public final class ReceiptDtos {

    private ReceiptDtos() {}

    public record AllocationInput(UUID invoiceId, BigDecimal amount) {}

    /**
     * Money received from a customer. {@code allocations} says which invoices it settles; when empty and
     * {@code autoAllocate} is true it settles the customer's oldest open invoices first. Anything left stays on the
     * customer's account as credit.
     */
    public record ReceiveMoneyRequest(UUID customerId, BigDecimal amount, LocalDate receivedOn, String method,
                                      String reference, UUID bankAccountId, String notes,
                                      List<AllocationInput> allocations, Boolean autoAllocate) {}

    public record AllocateRequest(List<AllocationInput> allocations) {}

    public record AllocationResponse(UUID invoiceId, String invoiceNumber, BigDecimal amount, Instant allocatedAt) {}

    public record ReceiptResponse(UUID id, String receiptNumber, UUID customerId, String kind, BigDecimal amount,
                                  BigDecimal allocated, BigDecimal available, LocalDate receivedOn, String method,
                                  String reference, UUID bankAccountId, String notes, Instant createdAt,
                                  List<AllocationResponse> allocations) {}

    public record OpenInvoice(UUID id, String invoiceNumber, LocalDate dueDate, BigDecimal total, BigDecimal amountPaid,
                              BigDecimal creditedAmount, BigDecimal balanceDue, String status) {}

    /** What a customer owes and the credit they have to use. */
    public record CustomerAccount(UUID customerId, BigDecimal owed, BigDecimal creditAvailable,
                                  List<OpenInvoice> openInvoices, List<ReceiptResponse> creditReceipts) {}
}
