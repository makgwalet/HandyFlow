package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Completes a retread on a tyre that is AWAITING_RETREAD: returns it to stock with a fresh tread. */
public record CompleteRetreadRequest(
        LocalDate retreadedAt,                              // defaults to today
        @Size(max = 255) String supplier,
        @PositiveOrZero BigDecimal cost,
        @NotNull @Positive @DecimalMax("30.0") BigDecimal newTreadDepthMm,
        @Size(max = 500) String notes
) {}