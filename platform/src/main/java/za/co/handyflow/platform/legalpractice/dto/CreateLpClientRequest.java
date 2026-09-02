package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLpClientRequest(
        @NotBlank String name,
        String email,
        String phone,
        @NotBlank String clientType,
        String idOrRegistrationNumber,
        String notes
) {}
