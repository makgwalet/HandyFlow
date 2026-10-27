package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.Set;

/** Working window, days (MON..SUN, first three letters are enough) and time zone (default Africa/Johannesburg). */
public record UpsertFmBusinessCalendarRequest(
        @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotEmpty Set<String> workingDays, String timeZone
) {}