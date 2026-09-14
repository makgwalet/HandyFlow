package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUniverseEntryRequest(
        @NotBlank String name,
        String description,
        String processArea,
        String glAccountGroup
) {}
