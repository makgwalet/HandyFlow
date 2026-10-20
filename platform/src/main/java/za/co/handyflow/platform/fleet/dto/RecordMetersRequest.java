package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Record hour-meter readings; give at least one. Meters only count up, PTO and hydraulic hours cannot exceed
 * engine hours, and readings must be in time order ({@code recordedAt} defaults to now).
 */
public record RecordMetersRequest(
        @PositiveOrZero BigDecimal engineHours,
        @PositiveOrZero BigDecimal ptoHours,
        @PositiveOrZero BigDecimal hydraulicHours,
        Instant recordedAt
) {}