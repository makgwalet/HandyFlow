package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyHoursRulesTest {

    // Week of Mon 16 Nov 2026 SAST = 2026-11-15T22:00Z .. 2026-11-22T22:00Z
    private static final Instant WS = Instant.parse("2026-11-15T22:00:00Z");
    private static final Instant WE = Instant.parse("2026-11-22T22:00:00Z");
    private static final Thresholds T = Thresholds.DEFAULTS;

    private static Slot slot(String a, String b) { return new Slot(Instant.parse(a), Instant.parse(b)); }
    /** n back-to-back-free slots of the given hours, one per day, each starting 04:00Z from Monday. */
    private static List<Slot> days(int n, int hours) {
        List<Slot> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Instant s = Instant.parse("2026-11-16T04:00:00Z").plusSeconds(i * 86400L);
            l.add(new Slot(s, s.plusSeconds(hours * 3600L)));
        }
        return l;
    }
    private static Assessment a(List<Slot> s) { return WeeklyHoursRules.assess(s, WS, WE, T); }

    @Test
    void weekRunsMondayToMondayInSouthAfricanTime() {
        for (LocalDate d : new LocalDate[]{LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)}) {
            Instant[] w = WeeklyHoursRules.week(d);
            assertEquals(WS, w[0]);
            assertEquals(WE, w[1]);
        }
        assertEquals(Instant.parse("2026-11-22T22:00:00Z"), WeeklyHoursRules.week(LocalDate.of(2026, 11, 23))[0]);
    }

    @Test
    void thresholdBoundaries() {
        assertEquals(State.OK, WeeklyHoursRules.stateFor(39.9, T));
        assertEquals(State.NEAR, WeeklyHoursRules.stateFor(40, T));
        assertEquals(State.NEAR, WeeklyHoursRules.stateFor(45, T));
        assertEquals(State.OVER, WeeklyHoursRules.stateFor(45.1, T));
        assertEquals(State.OVER, WeeklyHoursRules.stateFor(55, T));
        assertEquals(State.EXCEEDS, WeeklyHoursRules.stateFor(55.1, T));
    }

    @Test
    void fourTwelveHourShiftsIsOver() {
        Assessment r = a(days(4, 12));
        assertEquals(48.0, r.hours());
        assertEquals(State.OVER, r.state());
        assertEquals(4, r.shifts());
        assertTrue(r.flagged());
    }

    @Test
    void ordinaryWeekIsNotFlagged() {
        Assessment r = a(days(5, 8));
        assertEquals(40.0, r.hours());
        assertEquals(State.NEAR, r.state());
        Assessment q = a(days(4, 8));
        assertEquals(State.OK, q.state());
        assertFalse(q.flagged());
    }

    @Test
    void doubleBookingIsFlaggedNotCountedTwice() {
        Assessment r = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"), slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z")));
        assertEquals(12.0, r.hours());
        assertTrue(r.overlaps());
        assertTrue(r.flagged());
        Assessment partial = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"), slot("2026-11-16T10:00:00Z", "2026-11-16T22:00:00Z")));
        assertEquals(18.0, partial.hours());
        assertTrue(partial.overlaps());
    }

    @Test
    void backToBackShiftsAreContinuousNotOverlapping() {
        Assessment r = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"), slot("2026-11-16T16:00:00Z", "2026-11-17T04:00:00Z")));
        assertEquals(24.0, r.hours());
        assertFalse(r.overlaps());
        assertTrue(r.restBreaches().isEmpty());
    }

    @Test
    void shortRestBetweenShiftsIsReported() {
        Assessment r = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"), slot("2026-11-16T20:00:00Z", "2026-11-17T04:00:00Z")));
        assertEquals(1, r.restBreaches().size());
        assertEquals(4.0, r.restBreaches().get(0).hours());
        assertTrue(r.flagged());
    }

    @Test
    void exactlyTwelveHoursRestIsFine() {
        Assessment r = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"), slot("2026-11-17T04:00:00Z", "2026-11-17T16:00:00Z")));
        assertTrue(r.restBreaches().isEmpty());
        Assessment s = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"), slot("2026-11-17T03:59:00Z", "2026-11-17T16:00:00Z")));
        assertEquals(1, s.restBreaches().size());
    }

    @Test
    void sundayNightShiftIsClippedToTheWeek() {
        // Sun 15 Nov 22:00 SAST (20:00Z) to Mon 16 Nov 06:00 SAST (04:00Z): only the 6 h after Monday 00:00 SAST count
        Assessment r = a(List.of(slot("2026-11-15T20:00:00Z", "2026-11-16T04:00:00Z")));
        assertEquals(6.0, r.hours());
        assertEquals(1, r.shifts());
    }

    @Test
    void restAtTheStartOfTheWeekUsesTheDayBefore() {
        // ends Sun 22:00 SAST (20:00Z); next starts Mon 06:00 SAST (04:00Z) = 8 h rest, and the next shift is in the week
        Assessment r = a(List.of(slot("2026-11-15T08:00:00Z", "2026-11-15T20:00:00Z"), slot("2026-11-16T04:00:00Z", "2026-11-16T14:00:00Z")));
        assertEquals(1, r.restBreaches().size());
        assertEquals(8.0, r.restBreaches().get(0).hours());
        assertEquals(10.0, r.hours()); // the Sunday shift is outside the week
        assertEquals(1, r.shifts());
    }

    @Test
    void aShortRestIntoNextWeekIsNotThisWeeksProblem() {
        Assessment r = a(List.of(slot("2026-11-22T08:00:00Z", "2026-11-22T20:00:00Z"), slot("2026-11-23T01:00:00Z", "2026-11-23T10:00:00Z")));
        assertTrue(r.restBreaches().isEmpty());
    }

    @Test
    void emptyAndZeroLengthSlotsAreIgnored() {
        assertEquals(0.0, a(List.of()).hours());
        Assessment z = a(List.of(slot("2026-11-16T04:00:00Z", "2026-11-16T04:00:00Z")));
        assertEquals(0.0, z.hours());
        assertFalse(z.flagged());
    }

    // ---- source scans ----

    private static String svc() throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/WeeklyHoursService.java"));
    }

    @Test
    void onlyRealRosterCounts() throws Exception {
        String s = svc();
        assertTrue(s.contains("status IN ('SCHEDULED','ACTIVE','COMPLETED')"));
        assertTrue(s.contains("sh.tenant_id = ? AND sh.deleted_at IS NULL"));
    }

    @Test
    void limitsAreConfigurableWithTheStatedDefaults() throws Exception {
        String s = svc();
        assertTrue(s.contains("${handyflow.security.hours.warn-at:40}"));
        assertTrue(s.contains("${handyflow.security.hours.limit:45}"));
        assertTrue(s.contains("${handyflow.security.hours.ceiling:55}"));
        assertTrue(s.contains("${handyflow.security.hours.min-rest:12}"));
    }

    @Test
    void endpointNeedsReadPermission() throws Exception {
        String c = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/api/WeeklyHoursController.java"));
        int i = c.indexOf("weekly-hours");
        assertTrue(c.substring(i, i + 120).contains("SECURITY_READ"));
    }
}
