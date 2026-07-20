package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCoaAccountRequest(
        @NotBlank String accountCode,
        @NotBlank String accountName,
        @NotBlank String accountType,   // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
        String subType,
        boolean vatApplicable,
        String vatType                  // OUTPUT, INPUT, EXEMPT, ZERO_RATED, or null
) {
}