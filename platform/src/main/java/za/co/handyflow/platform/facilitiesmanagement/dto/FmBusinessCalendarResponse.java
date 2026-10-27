package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** {@code configured=false} means the tenant hasn't set a calendar and the defaults (Mon-Fri 08:00-17:00, SAST, no holidays) apply. */
public record FmBusinessCalendarResponse(
        boolean configured, LocalTime startTime, LocalTime endTime, List<String> workingDays,
        String timeZone, List<HolidayDto> holidays
) {
    public record HolidayDto(LocalDate date, String name) {}
}