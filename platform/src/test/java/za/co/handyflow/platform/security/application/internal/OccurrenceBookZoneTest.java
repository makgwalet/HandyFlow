package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** The Occurrence Book's day is the company's day, not South Africa's by default. */
class OccurrenceBookZoneTest {

    private static final ZoneId AKL = ZoneId.of("Pacific/Auckland");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static String src(String f) throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/" + f));
    }

    @Test
    void anInstantBelongsToTheDayOfTheCompanyZone() {
        Instant at = Instant.parse("2026-11-20T22:30:00Z");
        assertEquals(LocalDate.of(2026, 11, 21), OccurrenceBookRules.dayOf(at, ZoneId.of("Africa/Johannesburg")));
        assertEquals(LocalDate.of(2026, 11, 21), OccurrenceBookRules.dayOf(at, AKL));
        assertEquals(LocalDate.of(2026, 11, 20), OccurrenceBookRules.dayOf(at, UTC));
    }

    @Test
    void theOldFormStillMeansSouthAfrica() {
        Instant at = Instant.parse("2026-11-20T22:30:00Z");
        assertEquals(OccurrenceBookRules.dayOf(at, ZoneId.of("Africa/Johannesburg")), OccurrenceBookRules.dayOf(at));
    }

    @Test
    void aNullZoneFallsBackToSouthAfricaNotTheServer() {
        java.util.TimeZone before = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland"));
            Instant at = Instant.parse("2026-11-20T21:30:00Z"); // 23:30 on the 20th in SA, 10:30 on the 21st in Auckland
            assertEquals(LocalDate.of(2026, 11, 20), OccurrenceBookRules.dayOf(at, null));
            assertEquals(Instant.parse("2026-11-19T22:00:00Z"), OccurrenceBookRules.window(LocalDate.of(2026, 11, 20), null)[0]);
        } finally {
            java.util.TimeZone.setDefault(before);
        }
    }

    @Test
    void theWindowStartsAndEndsAtLocalMidnight() {
        Instant[] w = OccurrenceBookRules.window(LocalDate.of(2026, 11, 21), AKL);
        assertEquals(Instant.parse("2026-11-20T11:00:00Z"), w[0]);
        assertEquals(Instant.parse("2026-11-21T11:00:00Z"), w[1]);
        Instant[] u = OccurrenceBookRules.window(LocalDate.of(2026, 11, 21), UTC);
        assertEquals(Instant.parse("2026-11-21T00:00:00Z"), u[0]);
    }

    @Test
    void todayIsTheCompanysToday() {
        Instant now = Instant.parse("2026-11-20T22:30:00Z");
        assertEquals(LocalDate.of(2026, 11, 21), OccurrenceBookRules.dayOrToday(null, now, AKL));
        assertEquals(LocalDate.of(2026, 11, 20), OccurrenceBookRules.dayOrToday(null, now, UTC));
    }

    @Test
    void aDayIsFutureOnlyInTheCompanyZone() {
        Instant now = Instant.parse("2026-11-20T22:30:00Z");
        LocalDate tomorrowUtc = LocalDate.of(2026, 11, 21);
        assertEquals(tomorrowUtc, OccurrenceBookRules.dayOrToday(tomorrowUtc, now, AKL));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> OccurrenceBookRules.dayOrToday(tomorrowUtc, now, UTC));
        assertEquals("OB_FUTURE_DAY", e.getErrorCode());
    }

    @Test
    void signingFollowsTheCompanyDay() {
        Instant now = Instant.parse("2026-11-20T22:30:00Z");
        assertDoesNotThrow(() -> OccurrenceBookRules.requireSignable(LocalDate.of(2026, 11, 21), now, AKL));
        assertThrows(HandyFlowException.class, () -> OccurrenceBookRules.requireSignable(LocalDate.of(2026, 11, 21), now, UTC));
    }

    @Test
    void theServiceUsesTheCompanyZoneEverywhere() throws Exception {
        String s = src("OccurrenceBookService.java");
        assertTrue(s.contains("dayOrToday(asked, now, zone(tenantId))"));
        assertTrue(s.contains("window(date, zone(tenantId))"));
        assertTrue(s.contains("requireSignable(day, now, zone(tenantId))"));
        assertTrue(s.contains("dayOf(when, zone(tenantId))"));
        assertFalse(s.contains("OccurrenceBookRules.window(date)"));
        assertFalse(s.contains("dayOrToday(asked, now)"));
        assertTrue(s.contains("BusinessCalendar.zoneOf(calendar, tenantId)"));
        assertTrue(s.contains("required = false"));
    }
}
