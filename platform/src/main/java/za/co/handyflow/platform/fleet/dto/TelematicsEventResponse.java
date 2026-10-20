package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record TelematicsEventResponse(
        UUID id,
        UUID vehicleId,
        String registration,
        UUID driverId,
        String driverName,
        String type,
        String severity,
        Instant occurredAt,
        Double latitude,
        Double longitude,
        Double value,
        Double limitValue,
        Integer durationSeconds,
        String details,
        UUID geofenceId,
        String geofenceName,
        String source,                // DERIVED | PROVIDER
        boolean acknowledged,
        Instant acknowledgedAt
) {}