package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BatteryTestResponse(
        Instant testedAt,
        BigDecimal voltage,
        String result,
        String notes
) {}