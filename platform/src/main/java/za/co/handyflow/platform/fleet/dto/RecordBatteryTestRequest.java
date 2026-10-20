package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** A resting-voltage reading (engine off, battery at rest). The result is derived from it, not supplied. */
public record RecordBatteryTestRequest(
        @NotNull @Positive @DecimalMax("32.00") BigDecimal voltage,
        Instant testedAt,
        @Size(max = 500) String notes
) {}