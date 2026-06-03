package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Time entries
public record CreateTimeEntryRequest(
        @NotNull UUID clientId,
        UUID practitionerId,
        @NotNull LocalDate entryDate,
        @NotBlank String activityType,
        String description,
        @NotNull @DecimalMin("0.25") BigDecimal hours,
        @NotNull BigDecimal hourlyRate,
        boolean billable
) {
}
