package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AnimalResponse(
        UUID id,
        UUID farmId,
        UUID productionAreaId,
        UUID enterpriseId,
        UUID speciesId,
        String tagNumber,
        String name,
        String breed,
        String sex,
        LocalDate dateOfBirth,
        boolean estimatedAge,
        UUID sireId,
        UUID damId,
        String acquisitionType,
        LocalDate acquisitionDate,
        BigDecimal acquisitionCost,
        BigDecimal currentWeightKg,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
