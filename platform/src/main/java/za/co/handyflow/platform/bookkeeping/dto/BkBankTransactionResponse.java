package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BkBankTransactionResponse(
        UUID id, UUID clientId, UUID bankAccountId, LocalDate transactionDate, String description, String reference,
        BigDecimal amount, String transactionType, BigDecimal balanceAfter, boolean reconciled,
        Instant reconciledAt, UUID journalLineId, Instant createdAt
) {}
