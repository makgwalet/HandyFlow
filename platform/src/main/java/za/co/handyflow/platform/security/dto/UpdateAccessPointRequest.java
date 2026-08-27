package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccessPointRequest(
        @NotBlank String name,
        String description
) {}