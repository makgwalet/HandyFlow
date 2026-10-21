package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

/** Sets (or replaces) one salesperson's target for one month, e.g. period "2026-11". */
public record SetTargetRequest(
        @NotNull UUID ownerId,
        @NotNull @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Period must look like 2026-11") String period,
        @NotNull @DecimalMin(value = "0.01", message = "A target must be more than zero") @DecimalMax("9999999999999.99") BigDecimal amount
) {}
