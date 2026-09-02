package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLpClientRequest(
        @NotBlank String name,
        String email,
        String phone,
        String idOrRegistrationNumber,
        String notes
) {}
