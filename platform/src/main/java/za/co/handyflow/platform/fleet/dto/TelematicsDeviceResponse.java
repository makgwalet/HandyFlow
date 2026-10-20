package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record TelematicsDeviceResponse(
        UUID id,
        String provider,
        String externalId,
        UUID vehicleId,
        String registration,
        String name,
        boolean active,
        Instant lastSeenAt
) {}