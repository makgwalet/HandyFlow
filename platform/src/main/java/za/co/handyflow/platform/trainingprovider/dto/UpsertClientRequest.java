package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertClientRequest(
        @NotBlank String tradingName,
        String registrationNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address
) {}
