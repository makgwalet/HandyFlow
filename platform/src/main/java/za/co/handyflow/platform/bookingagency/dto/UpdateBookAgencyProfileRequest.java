package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBookAgencyProfileRequest(
        @NotBlank String agencyName,
        String registrationNumber,
        String email,
        String phone,
        String physicalAddress,
        String logoUrl
) {}