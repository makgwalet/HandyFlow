package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSlaClock;
import za.co.handyflow.platform.shared.TenantId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The tenant's business calendar for BUSINESS_HOURS SLA clocks: working window, working days, time zone and
 * public holidays. One per tenant. Holidays are entered by the tenant (they change year to year and are
 * sometimes moved), not hard-coded.
 */
@Entity
@Table(name = "fm_business_calendars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmBusinessCalendar {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "working_days", nullable = false, length = 30)
    private String workingDays; // e.g. MON,TUE,WED,THU,FRI

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fm_business_calendar_holidays", joinColumns = @JoinColumn(name = "calendar_id"))
    private List<FmHolidayEntry> holidays = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FmBusinessCalendar create(TenantId tenantId, LocalTime start, LocalTime end,
                                            Set<String> days, String timeZone) {
        FmBusinessCalendar c = new FmBusinessCalendar();
        c.tenantId = tenantId;
        c.createdAt = Instant.now();
        c.apply(start, end, days, timeZone);
        return c;
    }

    public void update(LocalTime start, LocalTime end, Set<String> days, String timeZone) {
        apply(start, end, days, timeZone);
    }

    private void apply(LocalTime start, LocalTime end, Set<String> days, String zone) {
        if (start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("endTime must be after startTime");
        if (days == null || days.isEmpty())
            throw new IllegalArgumentException("At least one working day is required");
        String zoneId = zone == null || zone.isBlank() ? "Africa/Johannesburg" : zone.trim();
        try {
            ZoneId.of(zoneId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown time zone: " + zoneId);
        }
        EnumSet<DayOfWeek> parsed = EnumSet.noneOf(DayOfWeek.class);
        for (String d : days) parsed.add(parseDay(d));

        this.startTime = start;
        this.endTime = end;
        this.workingDays = parsed.stream().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(","));
        this.timeZone = zoneId;
        this.updatedAt = Instant.now();
    }

    public void addHoliday(LocalDate date, String name) {
        if (date == null) throw new IllegalArgumentException("date is required");
        if (holidays.stream().anyMatch(h -> h.getDate().equals(date)))
            throw new IllegalStateException("A holiday is already recorded for " + date);
        holidays.add(FmHolidayEntry.of(date, name != null ? name.trim() : null));
        this.updatedAt = Instant.now();
    }

    public void removeHoliday(LocalDate date) {
        if (!holidays.removeIf(h -> h.getDate().equals(date)))
            throw new IllegalStateException("No holiday is recorded for " + date);
        this.updatedAt = Instant.now();
    }

    public Set<DayOfWeek> workingDaySet() {
        EnumSet<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (String d : workingDays.split(",")) set.add(parseDay(d));
        return set;
    }

    /** The calendar in the form the clock engine uses. */
    public FmSlaClock.Calendar toClockCalendar() {
        Set<LocalDate> dates = holidays.stream().map(FmHolidayEntry::getDate).collect(Collectors.toSet());
        return new FmSlaClock.Calendar(startTime, endTime, workingDaySet(), ZoneId.of(timeZone), dates);
    }

    private static DayOfWeek parseDay(String s) {
        String key = s == null ? "" : s.trim().toUpperCase();
        if (key.length() >= 3) {
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(key.substring(0, 3))) return d;
            }
        }
        throw new IllegalArgumentException("Unknown working day: " + s);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}