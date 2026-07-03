package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CompleteOperatorLogRequest(
        @NotNull Instant endedAt,
        BigDecimal endHours,
        BigDecimal fuelUsedLitres,
        String notes
) {}