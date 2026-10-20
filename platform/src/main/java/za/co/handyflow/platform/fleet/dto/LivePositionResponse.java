package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

/** A vehicle's latest known position, for the live map. */
public record LivePositionResponse(
        UUID vehicleId,
        String registration,
        String make,
        String model,
        double latitude,
        double longitude,
        Double speedKph,
        Integer heading,
        Boolean ignitionOn,
        Instant recordedAt,
        long ageMinutes,
        String status             // MOVING | IDLE | STOPPED | PARKED | OFFLINE
) {}