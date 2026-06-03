package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLineResponse(
        UUID id,
        UUID accountId,
        String accountCode,              // denormalised
        String accountName,              // denormalised
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal vatAmount,
        String vatType,
        int lineOrder
) {
}
