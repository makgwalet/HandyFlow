package za.co.handyflow.platform.ap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SupplierBankingRequest(
        @NotBlank String supplierName,
        String    bankName,
        String    accountHolder,
        @NotBlank String accountNumber,
        @NotBlank String branchCode,
        String    vatNumber,
        @Email    String email,
        String    notes
) {}