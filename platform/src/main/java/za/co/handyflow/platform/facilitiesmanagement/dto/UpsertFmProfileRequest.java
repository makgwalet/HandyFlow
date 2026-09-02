package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertFmProfileRequest(
        @NotBlank String companyName, String registrationNumber, String contactEmail,
        String contactPhone, String notes
) {}
