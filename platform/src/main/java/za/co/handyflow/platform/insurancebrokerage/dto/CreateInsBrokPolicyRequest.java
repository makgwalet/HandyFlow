package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Creates a new policy as a QUOTE — see InsBrokPolicy's own lifecycle Javadoc. */
public record CreateInsBrokPolicyRequest(
        @NotNull UUID clientId,
        @NotNull UUID insurerId,
        String quoteReference,
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
