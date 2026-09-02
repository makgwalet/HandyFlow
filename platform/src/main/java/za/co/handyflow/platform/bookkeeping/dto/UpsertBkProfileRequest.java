package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertBkProfileRequest(
        @NotBlank String practiceName, String registrationNumber, String contactEmail,
        String contactPhone, String notes
) {}
