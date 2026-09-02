package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BkBankAccountResponse(
        UUID id, UUID clientId, UUID accountId, String bankName, String accountName, String accountNumber,
        String branchCode, String accountType, String currency, BigDecimal currentBalance, boolean active, Instant createdAt
) {}
