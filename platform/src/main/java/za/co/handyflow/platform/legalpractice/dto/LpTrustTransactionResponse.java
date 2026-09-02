package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpTrustTransactionResponse(
        UUID id,
        UUID clientId,
        UUID matterId,
        String transactionType,
        BigDecimal amount,
        LocalDate transactionDate,
        UUID invoiceId,
        String payee,
        String reference,
        UUID capturedBy,
        String capturedByName,
        String notes,
        BigDecimal clientTrustBalanceAfter,
        Instant createdAt
) {}
