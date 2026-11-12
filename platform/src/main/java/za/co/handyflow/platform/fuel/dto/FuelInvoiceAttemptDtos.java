package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** G02: the trail of hand-offs from Fuel to Invoicing. */
public final class FuelInvoiceAttemptDtos {
    private FuelInvoiceAttemptDtos() {}

    public record AttemptView(UUID id, UUID customerId, String customerName, String status, boolean needsAttention,
                              List<String> receiptNumbers, int deliveryCount, UUID invoiceId, String invoiceNumber, String error,
                              UUID attemptedBy, Instant startedAt, Instant finishedAt, UUID resolvedBy, Instant resolvedAt, String resolutionNote) {}

    public record ResolveRequest(@NotBlank @Size(min = 5, max = 300, message = "Say what was done, in 5 to 300 characters") String note) {}
}
