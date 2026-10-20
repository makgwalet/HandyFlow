package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateEngineHoursRequest(
        @NotNull @PositiveOrZero BigDecimal engineHours
) {}