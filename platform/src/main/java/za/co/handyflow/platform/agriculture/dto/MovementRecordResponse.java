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
        Instant createdAt,
        String buyerName,
        String buyerIdNumber,
        String buyerAddress,
        String destination,
        String vehicleRegistration,
        /** Why the sale went ahead during a withdrawal period; null when nothing was held. */
        String withdrawalOverrideReason,
        /** The day that withdrawal period would have run to. */
        LocalDate withdrawalHeldUntil
) {
    /** Without the withdrawal override (callers written before it existed). */
    public MovementRecordResponse(UUID id, UUID animalId, UUID groupId, LocalDate movementDate, String movementType,
                                  UUID fromProductionAreaId, UUID toProductionAreaId, UUID fromFarmId, UUID toFarmId,
                                  Integer countMoved, String reason, String notes, Instant createdAt, String buyerName,
                                  String buyerIdNumber, String buyerAddress, String destination, String vehicleRegistration) {
        this(id, animalId, groupId, movementDate, movementType, fromProductionAreaId, toProductionAreaId, fromFarmId, toFarmId,
                countMoved, reason, notes, createdAt, buyerName, buyerIdNumber, buyerAddress, destination, vehicleRegistration, null, null);
    }

    public MovementRecordResponse(UUID id, UUID animalId, UUID groupId, LocalDate movementDate, String movementType,
                                  UUID fromProductionAreaId, UUID toProductionAreaId, UUID fromFarmId, UUID toFarmId,
                                  Integer countMoved, String reason, String notes, Instant createdAt) {
        this(id, animalId, groupId, movementDate, movementType, fromProductionAreaId, toProductionAreaId, fromFarmId, toFarmId,
                countMoved, reason, notes, createdAt, null, null, null, null, null, null, null);
    }
}
