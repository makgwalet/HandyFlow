package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateAnnualPlanRequest(
        @NotNull @Min(2020) @Max(2100) Integer planYear
) {}
