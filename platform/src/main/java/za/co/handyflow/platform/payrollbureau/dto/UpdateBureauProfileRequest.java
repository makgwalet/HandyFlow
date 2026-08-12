package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBureauProfileRequest(
        @NotBlank String firmName,
        String registrationNumber,
        String sdlNumber,
        String email,
        String phone,
        String physicalAddress,
        String logoUrl
) {}