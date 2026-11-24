package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** November 2026: Mon 23, Tue 24, Wed 25, Thu 26, Fri 27, Sat 28, Sun 29, Mon 30, Tue 1 Dec. */
class ApprovalWorkingTimeTest {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final ApprovalWorkingTime WEEK = ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(17, 0), ApprovalWorkingTime.MON_TO_FRI, Set.of());

    static Instant at(String local) { return LocalDateTime.parse(local).atZone(ZONE).toInstant(); }

    @Test
    void calendarClockCountsEverything() {
        ApprovalWorkingTime c = ApprovalWorkingTime.calendar(ZONE);
        assertEquals(1440, c.minutesBetween(at("2026-11-23T08:00"), at("2026-11-24T08:00")));
        assertEquals(72 * 60, c.minutesBetween(at("2026-11-27T16:00"), at("2026-11-30T16:00")));   // over a weekend
        assertTrue(c.isContinuous());
        assertTrue(c.isWorkingDay(LocalDate.of(2026, 11, 28)));
    }

    @Test
    void businessClockCountsOnlyWorkingHours() {
        assertEquals(150, WEEK.minutesBetween(at("2026-11-23T09:00"), at("2026-11-23T11:30")));
        assertEquals(540, WEEK.minutesBetween(at("2026-11-23T08:00"), at("2026-11-23T17:00")));       // a full working day
        assertEquals(120, WEEK.minutesBetween(at("2026-11-23T16:00"), at("2026-11-24T09:00")));       // an hour before close, an hour after open
        assertEquals(1620, WEEK.minutesBetween(at("2026-11-23T08:00"), at("2026-11-26T08:00")));      // three full days
    }

    @Test
    void timeBeforeOpeningAndAfterClosingDoesNotCount() {
        assertEquals(30, WEEK.minutesBetween(at("2026-11-23T06:00"), at("2026-11-23T08:30")));
        assertEquals(15, WEEK.minutesBetween(at("2026-11-23T18:00"), at("2026-11-24T08:15")));
        assertEquals(0, WEEK.minutesBetween(at("2026-11-23T17:00"), at("2026-11-24T08:00")));
    }

    @Test
    void weekendsDoNotCount() {
        assertEquals(120, WEEK.minutesBetween(at("2026-11-27T16:00"), at("2026-11-30T09:00")));
        assertEquals(0, WEEK.minutesBetween(at("2026-11-28T09:00"), at("2026-11-29T17:00")));
        assertFalse(WEEK.isWorkingDay(LocalDate.of(2026, 11, 28)));
    }

    @Test
    void closedDatesDoNotCount() {
        ApprovalWorkingTime closedMonday = ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(17, 0),
                ApprovalWorkingTime.MON_TO_FRI, Set.of(LocalDate.of(2026, 11, 30)));
        assertEquals(120, closedMonday.minutesBetween(at("2026-11-27T16:00"), at("2026-12-01T09:00")));   // Monday the 30th is skipped
        assertFalse(closedMonday.isWorkingDay(LocalDate.of(2026, 11, 30)));
    }

    @Test
    void anythingNotAfterTheStartIsZero() {
        assertEquals(0, WEEK.minutesBetween(at("2026-11-23T10:00"), at("2026-11-23T10:00")));
        assertEquals(0, WEEK.minutesBetween(at("2026-11-23T11:00"), at("2026-11-23T10:00")));
        assertEquals(0, WEEK.minutesBetween(null, at("2026-11-23T10:00")));
    }

    @Test
    void whenTheWaitWillHavePassed() {
        assertEquals(at("2026-11-30T09:00"), WEEK.addMinutes(at("2026-11-27T16:00"), 120));            // an hour on Friday, an hour on Monday
        assertEquals(at("2026-11-30T08:30"), WEEK.addMinutes(at("2026-11-28T10:00"), 30));             // started on a Saturday
        assertEquals(at("2026-11-23T17:00"), WEEK.addMinutes(at("2026-11-23T09:00"), 480));            // exactly to closing time
        assertEquals(at("2026-11-23T10:00"), WEEK.addMinutes(at("2026-11-23T07:00"), 120));            // started before opening
        assertEquals(at("2026-11-23T09:00"), WEEK.addMinutes(at("2026-11-23T09:00"), 0));
    }

    @Test
    void addMinutesSkipsClosedDates() {
        ApprovalWorkingTime closedMonday = ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(17, 0),
                ApprovalWorkingTime.MON_TO_FRI, Set.of(LocalDate.of(2026, 11, 30)));
        assertEquals(at("2026-12-01T09:00"), closedMonday.addMinutes(at("2026-11-27T16:00"), 120));
    }

    @Test
    void addMinutesAndMinutesBetweenAgree() {
        String[] starts = {"2026-11-23T07:15", "2026-11-23T08:00", "2026-11-25T16:59", "2026-11-27T12:00", "2026-11-28T09:00", "2026-11-29T23:00"};
        long[] waits = {1, 59, 60, 480, 541, 1440, 3000};
        for (String s : starts)
            for (long w : waits) {
                Instant from = at(s);
                assertEquals(w, WEEK.minutesBetween(from, WEEK.addMinutes(from, w)), "from " + s + " wait " + w);
            }
        ApprovalWorkingTime cal = ApprovalWorkingTime.calendar(ZONE);
        assertEquals(at("2026-11-24T08:00"), cal.addMinutes(at("2026-11-23T08:00"), 1440));
    }

    @Test
    void theCompanysOwnZoneDecidesWhereTheDayStarts() {
        ApprovalWorkingTime utc = ApprovalWorkingTime.businessHours(ZoneId.of("UTC"), LocalTime.of(8, 0), LocalTime.of(17, 0), ApprovalWorkingTime.MON_TO_FRI, Set.of());
        Instant from = Instant.parse("2026-11-23T06:00:00Z");   // 08:00 in Johannesburg, 06:00 in UTC (before opening)
        Instant to = Instant.parse("2026-11-23T09:00:00Z");
        assertEquals(60, utc.minutesBetween(from, to));
        assertEquals(180, WEEK.minutesBetween(from, to));
    }

    @Test
    void badWorkingHoursAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(17, 0), LocalTime.of(8, 0), ApprovalWorkingTime.MON_TO_FRI, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(8, 0), ApprovalWorkingTime.MON_TO_FRI, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(17, 0), Set.<DayOfWeek>of(), Set.of()));
    }
}
