package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpsertProfileRequest(
        @NotBlank String tradingName,
        String registrationNumber,
        String accreditationBody,
        String accreditationNumber,
        LocalDate accreditationExpiry,
        String address,
        String phone,
        String email
) {}
