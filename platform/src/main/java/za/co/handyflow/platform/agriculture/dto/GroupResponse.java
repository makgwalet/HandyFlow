package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        UUID farmId,
        UUID productionAreaId,
        UUID enterpriseId,
        UUID speciesId,
        String batchNumber,
        String breed,
        Integer initialCount,
        Integer currentCount,
        BigDecimal averageWeightKg,
        LocalDate originDate,
        String acquisitionType,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
