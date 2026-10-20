package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Restarts the hour-based service interval. {@code hours} defaults to the asset's current engine hours. */
public record RecordServiceDoneRequest(
        @PositiveOrZero BigDecimal hours
) {}