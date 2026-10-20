package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record RecordTreadMeasurementRequest(
        @NotNull @Positive @DecimalMax("30.0") BigDecimal treadDepthMm,
        @PositiveOrZero Integer pressureKpa,
        @PositiveOrZero Integer odometer,
        Instant measuredAt,
        @Size(max = 500) String notes
) {}