package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductionAreaRequest(
        @NotNull UUID farmId,
        @NotBlank String name,
        @NotBlank String areaType,
        BigDecimal sizeHectares,
        Integer capacity,
        String soilType
) {}
