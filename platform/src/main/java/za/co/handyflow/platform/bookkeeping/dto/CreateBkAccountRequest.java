package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;

/** Client-specific chart-of-accounts addition — always {@code system=false} (see {@code BkAccountService.createCustomAccount}). */
public record CreateBkAccountRequest(
        @NotBlank String accountCode, @NotBlank String accountName, @NotBlank String accountType,
        String accountSubtype, String description
) {}
