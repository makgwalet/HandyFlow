package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** List / vehicle-view summary of a tyre. */
public record TyreResponse(
        UUID id,
        String serialNumber,
        String brand,
        String pattern,
        String size,
        String status,
        UUID vehicleId,
        String registration,
        String position,
        BigDecimal lastTreadDepthMm,
        String treadStatus,           // OK | REPLACE_SOON | REPLACE_NOW | NO_READING
        Instant lastMeasuredAt,
        int kmInService,              // lifetime km, including the current fitment
        int retreadCount
) {}