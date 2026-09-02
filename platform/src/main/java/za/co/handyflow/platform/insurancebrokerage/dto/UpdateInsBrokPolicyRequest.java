package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateInsBrokPolicyRequest(
        @NotNull UUID insurerId,
        @NotBlank String lineOfBusiness,
        String assetType,
        String assetReference,
        BigDecimal sumInsured,
        BigDecimal premiumAmount,
        String premiumFrequency,
        BigDecimal excessAmount,
        BigDecimal commissionRatePct,
        LocalDate startDate,
        LocalDate expiryDate,
        String notes
) {}
