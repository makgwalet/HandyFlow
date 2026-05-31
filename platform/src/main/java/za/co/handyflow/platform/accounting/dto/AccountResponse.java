package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountCode,
        String accountName,
        String accountType,
        String accountSubtype,
        boolean isSystem,
        BigDecimal openingBalance,
        String description
) {}