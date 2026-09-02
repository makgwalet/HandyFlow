package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMovementRecordRequest(
        UUID animalId,
        UUID groupId,
        @NotNull LocalDate movementDate,
        @NotBlank String movementType,
        UUID fromProductionAreaId,
        UUID toProductionAreaId,
        UUID fromFarmId,
        UUID toFarmId,
        Integer countMoved,
        String reason,
        String notes
) {}
