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
        Instant createdAt,
        /** Why the harvest went ahead inside a pre-harvest interval; null when nothing was held. */
        String withdrawalOverrideReason,
        /** The day that interval would have run to. */
        LocalDate withdrawalHeldUntil
) {
    /** Without the override (callers written before it existed). */
    public HarvestRecordResponse(UUID id, UUID cropCycleId, LocalDate harvestDate, BigDecimal quantityHarvested, String unitOfMeasure,
                                 String qualityGrade, BigDecimal moistureContent, String storageLocation, UUID harvestedBy,
                                 String harvestedByName, BigDecimal laborHours, String notes, Instant createdAt) {
        this(id, cropCycleId, harvestDate, quantityHarvested, unitOfMeasure, qualityGrade, moistureContent, storageLocation,
                harvestedBy, harvestedByName, laborHours, notes, createdAt, null, null);
    }
}
