package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSeasonRequest(
        @NotNull UUID farmId,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
