package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmBusinessCalendar;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmHolidayEntry;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmBusinessCalendarRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.AddFmHolidayRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmBusinessCalendarResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmBusinessCalendarRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The tenant's business calendar for BUSINESS_HOURS SLA clocks. A calendar change moves future SLA deadlines
 * only — work orders already raised keep the due times they were stamped with. Changes are audited (a wrong
 * holiday list silently shifts every business-hours deadline).
 */
@Service
@RequiredArgsConstructor
public class FmBusinessCalendarService {

    private final FmBusinessCalendarRepository calendarRepository;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public FmBusinessCalendarResponse get(TenantId tenantId) {
        return calendarRepository.findByTenant(tenantId).map(this::toResponse).orElseGet(this::defaultsResponse);
    }

    @Transactional
    public FmBusinessCalendarResponse upsert(TenantId tenantId, UpsertFmBusinessCalendarRequest req) {
        FmBusinessCalendar calendar = calendarRepository.findByTenant(tenantId).orElse(null);
        String before = calendar == null ? "defaults" : describe(calendar);
        if (calendar == null) {
            calendar = FmBusinessCalendar.create(tenantId, req.startTime(), req.endTime(), req.workingDays(), req.timeZone());
        } else {
            calendar.update(req.startTime(), req.endTime(), req.workingDays(), req.timeZone());
        }
        calendarRepository.save(calendar);
        auditService.record(tenantId, "SLA_CALENDAR_CHANGED", "CALENDAR", calendar.getId(), before + " -> " + describe(calendar));
        return toResponse(calendar);
    }

    @Transactional
    public FmBusinessCalendarResponse addHoliday(TenantId tenantId, AddFmHolidayRequest req) {
        FmBusinessCalendar calendar = calendarRepository.findByTenant(tenantId)
                .orElseGet(() -> FmBusinessCalendar.create(tenantId, LocalTime.of(8, 0), LocalTime.of(17, 0),
                        Set.of("MON", "TUE", "WED", "THU", "FRI"), "Africa/Johannesburg"));
        calendar.addHoliday(req.date(), req.name());
        calendarRepository.save(calendar);
        auditService.record(tenantId, "SLA_HOLIDAY_ADDED", "CALENDAR", calendar.getId(), req.date() + " " + req.name());
        return toResponse(calendar);
    }

    @Transactional
    public FmBusinessCalendarResponse removeHoliday(TenantId tenantId, LocalDate date) {
        FmBusinessCalendar calendar = calendarRepository.findByTenant(tenantId)
                .orElseThrow(() -> new IllegalStateException("No business calendar is configured"));
        calendar.removeHoliday(date);
        calendarRepository.save(calendar);
        auditService.record(tenantId, "SLA_HOLIDAY_REMOVED", "CALENDAR", calendar.getId(), date.toString());
        return toResponse(calendar);
    }

    private String describe(FmBusinessCalendar c) {
        return c.getStartTime() + "-" + c.getEndTime() + " " + c.getWorkingDays() + " " + c.getTimeZone()
                + " holidays=" + c.getHolidays().size();
    }

    private FmBusinessCalendarResponse defaultsResponse() {
        FmSlaClock.Calendar d = FmSlaClock.Calendar.defaults();
        return new FmBusinessCalendarResponse(false, d.start(), d.end(), dayNames(d.workingDays()), d.zone().getId(), List.of());
    }

    private FmBusinessCalendarResponse toResponse(FmBusinessCalendar c) {
        List<FmBusinessCalendarResponse.HolidayDto> holidays = c.getHolidays().stream()
                .sorted(Comparator.comparing(FmHolidayEntry::getDate))
                .map(h -> new FmBusinessCalendarResponse.HolidayDto(h.getDate(), h.getName()))
                .toList();
        return new FmBusinessCalendarResponse(true, c.getStartTime(), c.getEndTime(), dayNames(c.workingDaySet()),
                c.getTimeZone(), holidays);
    }

    private List<String> dayNames(Set<DayOfWeek> days) {
        return EnumSet.copyOf(days).stream().map(d -> d.name().substring(0, 3)).toList();
    }
}