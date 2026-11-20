package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** RECEIPT — money deposited into trust. No invoiceId/payee (the entity forbids both). */
public record RecordTrustReceiptRequest(
        UUID matterId,
        @NotNull BigDecimal amount,
        LocalDate transactionDate,
        String reference,
        String notes,
        /** Optional. The same key sent twice posts once. */
        @jakarta.validation.constraints.Size(max = 80) String idempotencyKey
) {}
