package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * TRANSFER_TO_BUSINESS — the firm draws its own earned fees out of trust.
 * {@code invoiceId} is required here at the request level too (the entity
 * enforces it regardless, but rejecting early gives a clearer error);
 * {@code LpTrustTransactionService.transferToBusiness()} additionally
 * verifies the invoice is SENT/PARTIALLY_PAID and belongs to this client.
 */
public record TransferToBusinessRequest(
        @NotNull UUID invoiceId,
        UUID matterId,
        @NotNull BigDecimal amount,
        LocalDate transactionDate,
        String reference,
        String notes
) {}
