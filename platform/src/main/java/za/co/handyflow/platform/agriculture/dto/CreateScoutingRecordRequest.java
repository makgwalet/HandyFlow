package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateScoutingRecordRequest(
        @NotNull LocalDate scoutingDate,
        @NotBlank String observationType,
        String severity,
        @NotBlank String description,
        String recommendedAction,
        UUID scoutedBy,
        LocalDate followUpDate,
        String notes
) {}
