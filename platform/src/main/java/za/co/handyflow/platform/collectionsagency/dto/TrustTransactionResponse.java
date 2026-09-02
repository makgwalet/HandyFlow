package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrustTransactionResponse(
        UUID id, UUID clientId, UUID debtorAccountId, String transactionType, BigDecimal amount,
        LocalDate transactionDate, String reference, String notes, UUID recordedByUserId, Instant createdAt
) {}
