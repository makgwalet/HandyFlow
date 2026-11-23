package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTimeTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static Instant at(String s) { return Instant.parse(s); }
    private static boolean in(Instant[] r, Instant t) { return !t.isBefore(r[0]) && t.isBefore(r[1]); }

    @Test
    void aShiftStartingJustBeforeAndJustAfterLocalMidnightFallsInTheRightMonth() {
        Instant oct = at("2026-10-31T21:30:00Z"); // 23:30 SAST on 31 Oct
        Instant nov = at("2026-10-31T22:30:00Z"); // 00:30 SAST on 1 Nov, still 31 Oct in UTC
        assertEquals(LocalDate.of(2026, 10, 31), BusinessTime.businessDateOf(SAST, oct));
        assertEquals(LocalDate.of(2026, 11, 1), BusinessTime.businessDateOf(SAST, nov));
        Instant[] october = BusinessTime.monthRange(SAST, YearMonth.of(2026, 10));
        Instant[] november = BusinessTime.monthRange(SAST, YearMonth.of(2026, 11));
        assertTrue(in(october, oct));
        assertFalse(in(october, nov));
        assertTrue(in(november, nov));
    }

    @Test
    void monthsStartAtLocalMidnightNotUtcMidnight() {
        Instant[] r = BusinessTime.monthRange(SAST, YearMonth.of(2026, 11));
        assertEquals(at("2026-10-31T22:00:00Z"), r[0]);
        assertEquals(at("2026-11-30T22:00:00Z"), r[1]);
        assertEquals(at("2026-12-31T22:00:00Z"), BusinessTime.monthRange(SAST, YearMonth.of(2026, 12))[1]);
    }

    @Test
    void rangesAreHalfOpenSoTheBoundaryBelongsToTheLaterOne() {
        Instant[] oct = BusinessTime.monthRange(SAST, YearMonth.of(2026, 10));
        Instant[] nov = BusinessTime.monthRange(SAST, YearMonth.of(2026, 11));
        assertEquals(oct[1], nov[0]);
        assertFalse(in(oct, nov[0]));
        assertTrue(in(nov, nov[0]));
        assertTrue(in(oct, oct[0]));
    }

    @Test
    void aWeekRunsMondayToMondayAndSundayBelongsToTheWeekBefore() {
        for (LocalDate d : new LocalDate[]{LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)}) {
            Instant[] w = BusinessTime.weekRange(SAST, d);
            assertEquals(at("2026-11-15T22:00:00Z"), w[0]);
            assertEquals(at("2026-11-22T22:00:00Z"), w[1]);
        }
        assertEquals(at("2026-11-22T22:00:00Z"), BusinessTime.weekRange(SAST, LocalDate.of(2026, 11, 23))[0]);
    }

    @Test
    void aDayIsLocalMidnightToLocalMidnightAndAClockChangeDayIsNot24Hours() {
        Instant[] d = BusinessTime.dayRange(SAST, LocalDate.of(2026, 11, 22));
        assertEquals(24 * 3600L, d[1].getEpochSecond() - d[0].getEpochSecond());
        ZoneId london = ZoneId.of("Europe/London");
        Instant[] spring = BusinessTime.dayRange(london, LocalDate.of(2026, 3, 29));
        Instant[] autumn = BusinessTime.dayRange(london, LocalDate.of(2026, 10, 25));
        assertEquals(23 * 3600L, spring[1].getEpochSecond() - spring[0].getEpochSecond());
        assertEquals(25 * 3600L, autumn[1].getEpochSecond() - autumn[0].getEpochSecond());
    }

    @Test
    void anotherZoneGivesItsOwnDays() {
        ZoneId windhoek = ZoneId.of("Africa/Windhoek"), utc = ZoneId.of("UTC");
        Instant t = at("2026-11-22T23:30:00Z");
        assertEquals(LocalDate.of(2026, 11, 22), BusinessTime.today(utc, t));
        assertEquals(LocalDate.of(2026, 11, 23), BusinessTime.today(SAST, t));
        assertEquals(LocalDate.of(2026, 11, 23), BusinessTime.today(windhoek, t));
        assertEquals(LocalDate.of(2026, 11, 22), BusinessTime.today(ZoneId.of("America/New_York"), t));
    }

    @Test
    void anUnknownOrMissingZoneFallsBackToSouthAfrica() {
        assertEquals(SAST, BusinessTime.zoneOrDefault(null));
        assertEquals(SAST, BusinessTime.zoneOrDefault("  "));
        assertEquals(SAST, BusinessTime.zoneOrDefault("Mars/Olympus"));
        assertEquals(ZoneId.of("Africa/Windhoek"), BusinessTime.zoneOrDefault(" Africa/Windhoek "));
        assertTrue(BusinessTime.validZone("Africa/Johannesburg"));
        assertFalse(BusinessTime.validZone("Mars/Olympus"));
        assertFalse(BusinessTime.validZone(null));
        assertFalse(BusinessTime.validZone(""));
    }

    @Test
    void displaysAlwaysNameTheZone() {
        assertEquals("22 Nov 2026 09:47 SAST", BusinessTime.format(SAST, at("2026-11-22T07:47:00Z")));
        assertEquals("1 Jan 2027 00:05 SAST", BusinessTime.format(SAST, at("2026-12-31T22:05:00Z")));
        assertEquals("22 Nov 2026 07:47 UTC", BusinessTime.format(ZoneId.of("UTC"), at("2026-11-22T07:47:00Z")));
        assertEquals("SAST", BusinessTime.abbreviation(SAST, at("2026-06-01T00:00:00Z")));
    }

    @Test
    void theResultDoesNotDependOnTheServersOwnZone() {
        TimeZone before = TimeZone.getDefault();
        try {
            Object[] reference = null;
            for (String z : new String[]{"UTC", "Africa/Johannesburg", "Pacific/Auckland", "America/Los_Angeles"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(z));
                Object[] now = {BusinessTime.monthRange(SAST, YearMonth.of(2026, 11))[0], BusinessTime.weekRange(SAST, LocalDate.of(2026, 11, 20))[1],
                        BusinessTime.businessDateOf(SAST, at("2026-10-31T22:30:00Z")), BusinessTime.format(SAST, at("2026-11-22T07:47:00Z"))};
                if (reference == null) reference = now;
                assertArrayEquals(reference, now, z);
            }
        } finally { TimeZone.setDefault(before); }
    }
}
