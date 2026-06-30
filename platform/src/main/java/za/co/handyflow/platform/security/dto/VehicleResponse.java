package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID    id,
        String  vehicleType,
        String  registration,
        String  makeModel,
        boolean armored,
        UUID    assignedDriverGuardId,
        String  assignedDriverName,
        String  status,
        String  notes,
        Instant createdAt
) {}
