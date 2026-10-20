package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InsuranceResponse(
        UUID id,
        UUID vehicleId,
        boolean insured,
        String insurer,
        String policyNumber,
        String policyType,
        LocalDate policyStart,
        LocalDate policyExpiry,
        Long daysToExpiry,      // null when no expiry; negative once expired
        BigDecimal premium,
        BigDecimal excess,
        String broker,
        String brokerContact,
        BigDecimal coverAmount,
        BigDecimal vehicleValue,
        boolean trackingRequired,
        String trackingProvider,
        String trackingDeviceNumber,
        boolean windscreenCover,
        BigDecimal thirdPartyCoverAmount,
        String specialConditions,
        Instant updatedAt
) {}