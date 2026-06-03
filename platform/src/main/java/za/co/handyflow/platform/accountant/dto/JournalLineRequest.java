package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLineRequest(
        @NotNull UUID accountId,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal vatAmount,
        String vatType
) {
}
