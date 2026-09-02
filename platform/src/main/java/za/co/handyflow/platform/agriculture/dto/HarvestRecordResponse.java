package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HarvestRecordResponse(
        UUID id,
        UUID cropCycleId,
        LocalDate harvestDate,
        BigDecimal quantityHarvested,
        String unitOfMeasure,
        String qualityGrade,
        BigDecimal moistureContent,
        String storageLocation,
        UUID harvestedBy,
        String harvestedByName,
        BigDecimal laborHours,
        String notes,
        Instant createdAt
) {}
