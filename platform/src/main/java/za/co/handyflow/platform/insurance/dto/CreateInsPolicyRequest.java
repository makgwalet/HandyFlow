package za.co.handyflow.platform.insurance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInsPolicyRequest(
        @NotBlank String policyNumber,
        @NotBlank String insurerName,
        @NotBlank String lineOfBusiness, // MOTOR | PROPERTY | EQUIPMENT | LIABILITY | OTHER
        String assetType, // VEHICLE | PROPERTY | EQUIPMENT | OTHER | null
        String assetReference,
        BigDecimal sumInsured,
        @NotNull BigDecimal premiumAmount,
        @NotBlank String premiumFrequency, // MONTHLY | QUARTERLY | ANNUAL
        BigDecimal excessAmount,
        String brokerOrInsurerContact,
        @NotNull LocalDate startDate,
        @NotNull LocalDate expiryDate,
        String notes
) {}
