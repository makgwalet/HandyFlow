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
        String notes,
        /** Only needed for a SALE or TRANSFER_OUT of an animal or group that is still in its withdrawal period. */
        String withdrawalOverrideReason,
        /** For a SALE or TRANSFER_OUT: who it went to and how, for the document of identification. All optional. */
        String buyerName,
        String buyerIdNumber,
        String buyerAddress,
        String destination,
        String vehicleRegistration
) {
    public CreateMovementRecordRequest(UUID animalId, UUID groupId, LocalDate movementDate, String movementType,
                                       UUID fromProductionAreaId, UUID toProductionAreaId, UUID fromFarmId, UUID toFarmId,
                                       Integer countMoved, String reason, String notes, String withdrawalOverrideReason) {
        this(animalId, groupId, movementDate, movementType, fromProductionAreaId, toProductionAreaId, fromFarmId, toFarmId,
                countMoved, reason, notes, withdrawalOverrideReason, null, null, null, null, null);
    }

    public CreateMovementRecordRequest(UUID animalId, UUID groupId, LocalDate movementDate, String movementType,
                                       UUID fromProductionAreaId, UUID toProductionAreaId, UUID fromFarmId, UUID toFarmId,
                                       Integer countMoved, String reason, String notes) {
        this(animalId, groupId, movementDate, movementType, fromProductionAreaId, toProductionAreaId, fromFarmId, toFarmId,
                countMoved, reason, notes, null, null, null, null, null, null);
    }
}
