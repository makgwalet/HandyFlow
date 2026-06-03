package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// SARS deadlines
public record GenerateDeadlinesRequest(
        @NotNull @Min(2020) Integer periodYear
) {
}
