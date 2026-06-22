package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BankTransactionResponse(
        UUID id,
        UUID bankAccountId,
        LocalDate transactionDate,
        String description,
        String reference,
        BigDecimal amount,
        String transactionType,   // CREDIT | DEBIT
        BigDecimal balanceAfter,
        boolean reconciled,
        Instant createdAt
) {}