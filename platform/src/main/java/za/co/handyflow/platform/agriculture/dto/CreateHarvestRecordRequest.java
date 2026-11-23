package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateHarvestRecordRequest(
        @NotNull LocalDate harvestDate,
        @NotNull BigDecimal quantityHarvested,
        @NotBlank String unitOfMeasure,
        String qualityGrade,
        BigDecimal moistureContent,
        String storageLocation,
        UUID harvestedBy,
        BigDecimal laborHours,
        String notes,
        /** Only needed to harvest while a spray's pre-harvest interval has not passed. */
        String preHarvestOverrideReason
) {
    public CreateHarvestRecordRequest(LocalDate harvestDate, BigDecimal quantityHarvested, String unitOfMeasure,
                                      String qualityGrade, BigDecimal moistureContent, String storageLocation,
                                      UUID harvestedBy, BigDecimal laborHours, String notes) {
        this(harvestDate, quantityHarvested, unitOfMeasure, qualityGrade, moistureContent, storageLocation, harvestedBy,
                laborHours, notes, null);
    }
}
