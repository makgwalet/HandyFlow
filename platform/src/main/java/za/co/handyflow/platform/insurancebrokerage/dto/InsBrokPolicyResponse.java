package za.co.handyflow.platform.insurancebrokerage.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InsBrokPolicyResponse(
        UUID id,
        UUID clientId,
        UUID insurerId,
        String policyNumber,
        String quoteReference,
        String lineOfBusiness,
        String assetType,
        String assetReference,
        BigDecimal sumInsured,
        BigDecimal premiumAmount,
        String premiumFrequency,
        BigDecimal excessAmount,
        BigDecimal commissionRatePct,
        LocalDate startDate,
        LocalDate expiryDate,
        String status,
        Instant boundAt,
        Instant activatedAt,
        LocalDate cancelledDate,
        String cancelReason,
        UUID renewalOfPolicyId,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
