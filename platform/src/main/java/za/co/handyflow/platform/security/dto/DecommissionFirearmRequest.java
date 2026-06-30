package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record DecommissionFirearmRequest(
        @NotBlank String reason
) {}
