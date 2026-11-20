package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ask for money to be paid out of a client's trust account. Nothing moves until a second person approves.
 * {@code paymentType} is TRANSFER_TO_BUSINESS (needs {@code invoiceId}), DISBURSEMENT_PAYMENT or REFUND (both need {@code payee}).
 * {@code idempotencyKey} (one per attempt) makes a double click or retry return the first request instead of queuing a second.
 */
public record CreateTrustPaymentRequest(
        @NotBlank String paymentType,
        UUID matterId,
        UUID invoiceId,
        @NotNull BigDecimal amount,
        LocalDate transactionDate,
        @Size(max = 255) String payee,
        @Size(max = 255) String reference,
        String notes,
        @Size(max = 80) String idempotencyKey
) {}
