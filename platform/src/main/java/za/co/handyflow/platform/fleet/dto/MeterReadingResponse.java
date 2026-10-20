package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MeterReadingResponse(
        Instant recordedAt,
        BigDecimal engineHours,
        BigDecimal ptoHours,
        BigDecimal hydraulicHours
) {}