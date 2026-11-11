package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BudgetLineRequest(
        @NotBlank String category,                       // VENUE, CATERING, AV_PRODUCTION, ... (case-insensitive)
        @NotBlank @Size(max = 200) String description,
        @NotNull @PositiveOrZero BigDecimal plannedAmount,
        @Size(max = 500) String notes
) {}