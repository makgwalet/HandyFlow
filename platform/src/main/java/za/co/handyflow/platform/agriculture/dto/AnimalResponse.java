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
        Instant updatedAt,
        boolean breedingStock // capital, not a production cost: its purchase price is left out of margins (ADR-001 decision 7)
) {}
