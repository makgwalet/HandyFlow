package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TyreMeasurementResponse(
        Instant measuredAt,
        BigDecimal treadDepthMm,
        String treadStatus,
        Integer pressureKpa,
        Integer odometer,
        String notes
) {}