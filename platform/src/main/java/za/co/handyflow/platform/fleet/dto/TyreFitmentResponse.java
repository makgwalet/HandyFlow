package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record TyreFitmentResponse(
        UUID id,
        UUID vehicleId,
        String registration,
        String position,
        Instant fittedAt,
        Integer fittedOdometer,
        Instant removedAt,
        Integer removedOdometer,
        Integer km,                   // null while still fitted
        String removalReason,
        UUID fitWorkOrderId,
        UUID removeWorkOrderId
) {}