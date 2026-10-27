package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFmClientRequest(
        @NotBlank String tradingName, String registrationNumber, String contactName,
        String contactEmail, String contactPhone, String address,
        /** Optional. 10 digits starting with 4 when the client is VAT-registered. */
        String vatNumber
) {}
