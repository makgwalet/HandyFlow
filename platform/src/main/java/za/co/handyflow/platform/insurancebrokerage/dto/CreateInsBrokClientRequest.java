package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateInsBrokClientRequest(
        @NotBlank String clientName,
        @NotBlank String clientType, // INDIVIDUAL | COMMERCIAL
        String registrationOrIdNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address,
        BigDecimal defaultCommissionRatePct,
        String notes
) {}
