package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BankAccountResponse(
        UUID id,
        String bankName,
        String accountName,
        String accountNumber,
        String branchCode,
        String accountType,
        String currency,
        BigDecimal currentBalance,
        boolean active
) {}