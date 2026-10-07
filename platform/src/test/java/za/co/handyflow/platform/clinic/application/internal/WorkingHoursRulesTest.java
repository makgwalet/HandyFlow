package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.WorkingHoursRules.Window;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkingHoursRulesTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    // 2026-10-07 is a Wednesday (day 3)
    private static final Instant WED_0900 = Instant.parse("2026-10-07T07:00:00Z");

    private static Window w(int day, String from, String to) {
        return new Window(day, LocalTime.parse(from), LocalTime.parse(to));
    }

    private static final List<Window> WEEK = List.of(
            w(3, "08:00", "12:00"), w(3, "13:00", "17:00"), w(1, "08:00", "16:00"));

    @Test
    void noHoursMeansNoRestriction() {
        assertNull(WorkingHoursRules.outsideHours("Dr A", List.of(), WED_0900, 30, SAST));
        assertNull(WorkingHoursRules.outsideHours("Dr A", null, WED_0900, 30, SAST));
    }

    @Test
    void aBookingInsideOneWindowFits() {
        assertNull(WorkingHoursRules.outsideHours("Dr A", WEEK, WED_0900, 30, SAST));
        // 16:30 to 17:00 ends exactly at closing time
        assertNull(WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-07T14:30:00Z"), 30, SAST));
    }

    @Test
    void aBookingOverTheLunchBreakIsRefused() {
        // 11:45 to 12:15 straddles the break
        String m = WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-07T09:45:00Z"), 30, SAST);
        assertNotNull(m);
        assertTrue(m.contains("Dr A"));
        assertTrue(m.contains("08:00–12:00, 13:00–17:00"), m);
    }

    @Test
    void aBookingAfterClosingIsRefused() {
        assertNotNull(WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-07T14:45:00Z"), 30, SAST)); // 16:45-17:15
        assertNotNull(WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-07T04:00:00Z"), 30, SAST)); // 06:00
    }

    @Test
    void aDayWithNoWindowsIsADayOff() {
        // Thursday 2026-10-08 09:00
        String m = WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-08T07:00:00Z"), 30, SAST);
        assertNotNull(m);
        assertTrue(m.contains("does not work on Thursdays"), m);
    }

    @Test
    void usesClinicTimeNotUtc() {
        // 06:30Z is 08:30 in Johannesburg: inside. 05:30Z would be 07:30: outside.
        assertNull(WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-07T06:30:00Z"), 30, SAST));
        assertNotNull(WorkingHoursRules.outsideHours("Dr A", WEEK, Instant.parse("2026-10-07T05:30:00Z"), 30, SAST));
    }

    @Test
    void aBookingRunningPastMidnightIsRefused() {
        List<Window> late = List.of(w(3, "08:00", "23:59"));
        assertNotNull(WorkingHoursRules.outsideHours("Dr A", late, Instant.parse("2026-10-07T21:30:00Z"), 120, SAST)); // 23:30 + 2h
    }

    @Test
    void namesTheDefaultWhenThereIsNoName() {
        assertTrue(WorkingHoursRules.outsideHours(null, WEEK, Instant.parse("2026-10-08T07:00:00Z"), 30, SAST).startsWith("This practitioner"));
    }

    @Test
    void validateSortsAndAcceptsAGoodWeek() {
        List<Window> out = WorkingHoursRules.validate(List.of(w(3, "13:00", "17:00"), w(1, "08:00", "16:00"), w(3, "08:00", "12:00")));
        assertEquals(1, out.get(0).dayOfWeek());
        assertEquals(LocalTime.of(8, 0), out.get(1).from());
        assertEquals(LocalTime.of(13, 0), out.get(2).from());
    }

    @Test
    void validateRefusesNonsense() {
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.validate(List.of(w(0, "08:00", "09:00"))));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.validate(List.of(w(8, "08:00", "09:00"))));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.validate(List.of(w(2, "09:00", "09:00"))));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.validate(List.of(w(2, "10:00", "09:00"))));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.validate(List.of(w(2, "08:00", "12:00"), w(2, "11:00", "15:00"))));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.validate(List.of(
                w(2, "06:00", "07:00"), w(2, "08:00", "09:00"), w(2, "10:00", "11:00"), w(2, "12:00", "13:00"), w(2, "14:00", "15:00"))));
    }

    @Test
    void touchingWindowsAreAllowed() {
        assertEquals(2, WorkingHoursRules.validate(List.of(w(2, "08:00", "12:00"), w(2, "12:00", "15:00"))).size());
    }

    @Test
    void parsesTimesAndRejectsGarbage() {
        assertEquals(LocalTime.of(8, 30), WorkingHoursRules.parse(" 08:30 "));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.parse("8.30am"));
        assertThrows(IllegalArgumentException.class, () -> WorkingHoursRules.parse(null));
        assertEquals("08:30", WorkingHoursRules.format(LocalTime.of(8, 30)));
    }
}
