package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBankAccountRequest(
        @NotBlank String bankName,
        @NotBlank String accountName,
        @NotBlank String accountNumber,
        String branchCode,
        String accountType
) {}