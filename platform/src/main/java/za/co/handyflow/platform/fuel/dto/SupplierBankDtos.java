package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** B04: supplier banking. Lists show only the last four digits of an account number. */
public final class SupplierBankDtos {
    private SupplierBankDtos() {}

    public record ProposeRequest(@NotBlank @Size(max = 150) String accountHolder, @NotBlank @Size(max = 100) String bankName, @Size(max = 20) String branchCode,
                                 @NotBlank @Size(max = 40) String accountNumber, @Size(max = 20) String accountType, @Size(max = 300) String reason) {}

    public record DecisionRequest(@NotBlank @Size(min = 5, max = 300, message = "Note must be 5 to 300 characters") String note) {}

    public record BankAccountView(UUID id, UUID supplierId, String accountHolder, String bankName, String branchCode, String accountNumberMasked,
                                  String accountType, String status, String reason, UUID proposedBy, Instant proposedAt, UUID decidedBy, Instant decidedAt, String decisionNote) {}

    public record RevealedAccount(UUID id, String accountNumber) {}
}
