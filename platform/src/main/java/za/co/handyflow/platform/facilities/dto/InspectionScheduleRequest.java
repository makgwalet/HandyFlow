package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Create: checklist, site, optional asset, how often, optional first due date (default: one interval from today). */
public record InspectionScheduleRequest(
        @NotNull UUID templateId, @NotNull UUID siteId, UUID assetId,
        @NotNull @Min(1) Integer frequencyDays, LocalDate firstDueDate
) {}
