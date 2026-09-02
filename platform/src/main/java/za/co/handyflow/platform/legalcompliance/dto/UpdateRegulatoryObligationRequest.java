package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.RecurrenceInterval;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateRegulatoryObligationRequest(
        @NotBlank String title,
        String regulationReference,
        String description,
        UUID responsibleUserId,
        String responsibleUserName,
        @NotNull LocalDate reviewDate,
        @NotNull RecurrenceInterval recurrence
) {}
