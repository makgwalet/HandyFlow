package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBkBankAccountRequest(
        @NotNull UUID clientId, @NotBlank String bankName, @NotBlank String accountName,
        @NotBlank String accountNumber, String branchCode, String accountType
) {}
