package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLpTimeEntryRequest(
        LocalDate entryDate,
        @NotNull BigDecimal hours,
        @NotNull BigDecimal hourlyRate,
        @NotBlank String description
) {}
