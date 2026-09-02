package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCropCycleRequest(
        @NotNull UUID farmId,
        @NotNull UUID productionAreaId,
        UUID enterpriseId,
        UUID seasonId,
        @NotNull UUID cropTypeId,
        String variety,
        String cycleName,
        @NotNull BigDecimal areaPlantedHectares,
        LocalDate plantingDate,
        LocalDate expectedHarvestDate,
        UUID seedInventoryItemId,
        BigDecimal seedQuantity,
        String seedSource,
        String notes
) {}
