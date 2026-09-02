package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BkAccountResponse(
        UUID id, UUID clientId, String accountCode, String accountName, String accountType,
        String accountSubtype, boolean system, BigDecimal openingBalance, String description, Instant createdAt
) {}
