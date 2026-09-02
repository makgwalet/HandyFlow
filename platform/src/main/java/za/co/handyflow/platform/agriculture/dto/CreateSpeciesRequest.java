package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateSpeciesRequest(
        @NotBlank String name,
        @NotBlank String category,
        String defaultUnitOfMeasure,
        String trackingMode,
        Integer gestationDays,
        BigDecimal maturityWeightKg
) {}
