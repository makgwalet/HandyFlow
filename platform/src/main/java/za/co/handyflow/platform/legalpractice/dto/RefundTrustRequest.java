package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** REFUND — trust money returned directly to the client. {@code payee} is required (the client, for the paper trail). */
public record RefundTrustRequest(
        UUID matterId,
        @NotNull BigDecimal amount,
        LocalDate transactionDate,
        @NotBlank String payee,
        String reference,
        String notes
) {}
