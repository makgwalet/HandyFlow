package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateHoursRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal currentHours
) {}
