package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEngagementRequest(
        UUID planEntryId,                 // nullable — an ad-hoc engagement (fraud tip-off, board request) has no plan entry, per the agreed design
        @NotNull UUID universeEntryId,
        @NotBlank String name,
        LocalDate startDate,
        LocalDate endDate
) {}
