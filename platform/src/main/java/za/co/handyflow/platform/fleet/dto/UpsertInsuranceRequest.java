package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.InsurancePolicyType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Full-replace of a vehicle's insurance profile. When {@code insured} is true, insurer and policyExpiry are required. */
public record UpsertInsuranceRequest(
        boolean insured,
        @Size(max = 255) String insurer,
        @Size(max = 100) String policyNumber,
        InsurancePolicyType policyType,
        LocalDate policyStart,
        LocalDate policyExpiry,
        @PositiveOrZero BigDecimal premium,
        @PositiveOrZero BigDecimal excess,
        @Size(max = 255) String broker,
        @Size(max = 255) String brokerContact,
        @PositiveOrZero BigDecimal coverAmount,
        @PositiveOrZero BigDecimal vehicleValue,
        boolean trackingRequired,
        @Size(max = 255) String trackingProvider,
        @Size(max = 100) String trackingDeviceNumber,
        boolean windscreenCover,
        @PositiveOrZero BigDecimal thirdPartyCoverAmount,
        String specialConditions
) {}