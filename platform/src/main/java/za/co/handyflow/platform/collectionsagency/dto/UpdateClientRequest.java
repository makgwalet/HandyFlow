package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record UpdateClientRequest(
        @NotBlank String tradingName, String registrationNumber, BigDecimal commissionRatePct, String contactName,
        String contactEmail, String contactPhone, String address, String notes
) {}
