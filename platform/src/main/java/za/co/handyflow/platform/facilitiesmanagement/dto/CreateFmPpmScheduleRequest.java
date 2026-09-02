package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateFmPpmScheduleRequest(
        @NotNull UUID assetId, @NotBlank String taskName, String description,
        @NotNull Integer frequencyDays, LocalDate startDate
) {}
