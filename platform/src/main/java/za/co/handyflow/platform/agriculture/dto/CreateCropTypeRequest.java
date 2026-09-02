package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCropTypeRequest(
        @NotBlank String name,
        @NotBlank String category,
        Integer typicalGrowingDays,
        String defaultUnitOfMeasure
) {}
