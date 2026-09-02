package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLpTimeEntryRequest(
        @NotNull UUID attorneyId,
        LocalDate entryDate,
        @NotNull BigDecimal hours,
        BigDecimal hourlyRate,
        @NotBlank String description,
        boolean billable
) {}
