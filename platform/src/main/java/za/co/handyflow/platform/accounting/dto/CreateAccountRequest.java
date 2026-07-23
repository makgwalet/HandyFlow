package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String accountCode,
        @NotBlank String accountName,
        @NotBlank String accountType,      // ASSET | LIABILITY | EQUITY | INCOME | EXPENSE
        String accountSubtype,
        String description
) {}