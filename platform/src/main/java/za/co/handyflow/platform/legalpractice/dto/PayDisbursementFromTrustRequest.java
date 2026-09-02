package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** DISBURSEMENT_PAYMENT — trust pays a third party directly on the client's behalf. {@code payee} is required. */
public record PayDisbursementFromTrustRequest(
        UUID matterId,
        @NotNull BigDecimal amount,
        LocalDate transactionDate,
        @NotBlank String payee,
        String reference,
        String notes
) {}
