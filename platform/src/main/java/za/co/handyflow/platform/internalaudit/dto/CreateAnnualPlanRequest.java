package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateAnnualPlanRequest(
        @NotNull(message = "Enter the plan year.")
        @Min(value = 2020, message = "The plan year must be 2020 or later.")
        @Max(value = 2100, message = "The plan year must be 2100 or earlier.") Integer planYear
) {}
