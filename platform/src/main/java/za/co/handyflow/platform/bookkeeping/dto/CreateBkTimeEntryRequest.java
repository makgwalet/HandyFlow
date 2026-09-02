package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBkTimeEntryRequest(
        @NotNull UUID clientId, UUID practitionerId, String practitionerName, @NotNull LocalDate entryDate,
        @NotNull String activityType, String description, @NotNull BigDecimal hours,
        @NotNull BigDecimal hourlyRate, boolean billable
) {}
