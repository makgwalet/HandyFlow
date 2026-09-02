package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MovementRecordResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        LocalDate movementDate,
        String movementType,
        UUID fromProductionAreaId,
        UUID toProductionAreaId,
        UUID fromFarmId,
        UUID toFarmId,
        Integer countMoved,
        String reason,
        String notes,
        Instant createdAt
) {}
