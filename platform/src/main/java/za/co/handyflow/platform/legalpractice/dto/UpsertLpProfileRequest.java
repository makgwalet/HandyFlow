package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

/** Create-or-update — one {@code LpProfile} row per tenant, same upsert shape as {@code AgencyProfileResponse}'s own request. */
public record UpsertLpProfileRequest(
        @NotBlank String firmName,
        String practiceNumber,
        String vatNumber,
        String contactEmail,
        String contactPhone,
        String trustBankName,
        String trustAccountNumber,
        String businessBankName,
        String businessAccountNumber
) {}
