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
        boolean breedingStock, // capital, not a production cost: its purchase price is left out of margins (ADR-001 decision 7)
        /** Why the animal left during a withdrawal period; null when nothing was held. */
        String withdrawalOverrideReason,
        /** The day that withdrawal period would have run to. */
        LocalDate withdrawalHeldUntil
) {
    /** Without the withdrawal override (callers written before it existed). */
    public AnimalResponse(UUID id, UUID farmId, UUID productionAreaId, UUID enterpriseId, UUID speciesId, String tagNumber, String name,
                          String breed, String sex, LocalDate dateOfBirth, boolean estimatedAge, UUID sireId, UUID damId,
                          String acquisitionType, LocalDate acquisitionDate, BigDecimal acquisitionCost, BigDecimal currentWeightKg,
                          String status, String notes, Instant createdAt, Instant updatedAt, boolean breedingStock) {
        this(id, farmId, productionAreaId, enterpriseId, speciesId, tagNumber, name, breed, sex, dateOfBirth, estimatedAge, sireId, damId,
                acquisitionType, acquisitionDate, acquisitionCost, currentWeightKg, status, notes, createdAt, updatedAt, breedingStock, null, null);
    }
}
