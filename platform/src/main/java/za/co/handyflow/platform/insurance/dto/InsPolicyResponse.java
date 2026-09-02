package za.co.handyflow.platform.insurance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InsPolicyResponse(
        UUID id,
        String policyNumber,
        String insurerName,
        String lineOfBusiness,
        String assetType,
        String assetReference,
        BigDecimal sumInsured,
        BigDecimal premiumAmount,
        String premiumFrequency,
        BigDecimal excessAmount,
        String brokerOrInsurerContact,
        LocalDate startDate,
        LocalDate expiryDate,
        String status,
        UUID renewalOfPolicyId,
        LocalDate cancelledDate,
        String cancelReason,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
