package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CropCycleResponse(
        UUID id,
        UUID farmId,
        UUID productionAreaId,
        UUID enterpriseId,
        UUID seasonId,
        UUID cropTypeId,
        String variety,
        String cycleName,
        BigDecimal areaPlantedHectares,
        LocalDate plantingDate,
        LocalDate expectedHarvestDate,
        UUID seedInventoryItemId,
        BigDecimal seedQuantity,
        String seedSource,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
