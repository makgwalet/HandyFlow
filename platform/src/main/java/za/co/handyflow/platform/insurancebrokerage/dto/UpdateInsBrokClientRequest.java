package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record UpdateInsBrokClientRequest(
        @NotBlank String clientName,
        @NotBlank String clientType,
        String registrationOrIdNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address,
        BigDecimal defaultCommissionRatePct,
        String notes
) {}
