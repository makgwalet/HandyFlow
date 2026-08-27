package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccessPointRequest(
        @NotNull UUID siteId,
        @NotBlank String name,
        String description
) {}