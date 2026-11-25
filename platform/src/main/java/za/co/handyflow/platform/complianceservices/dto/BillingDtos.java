package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {

    private BillingDtos() {}

    public record ReasonRequest(@Size(max = 2000) String reason) {}

    /** Where the invoice stands in Invoicing. All amounts are null until the invoice exists. */
    public record InvoiceInfo(String status, BigDecimal total, BigDecimal paid, BigDecimal credited) {}

    public record EventView(UUID id, UUID agreementId, String agreementNumber, UUID clientId, String clientName, LocalDate dueDate, BigDecimal amount,
                            String description, String status, String stage, long daysLate, UUID invoiceId, String invoiceNumber, InvoiceInfo invoice,
                            Instant raisedAt, String skipReason, String releaseNote, List<String> actions, String blocked) {}

    public record Generated(int created, List<EventView> events) {}
}
