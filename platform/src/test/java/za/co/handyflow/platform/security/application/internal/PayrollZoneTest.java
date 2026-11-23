package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** A pay period's days are the company's days. */
class PayrollZoneTest {

    private static final ZoneId AKL = ZoneId.of("Pacific/Auckland");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static String src(String f) throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/" + f));
    }

    @Test
    void periodWindowIsCutAtLocalMidnight() {
        assertEquals(Instant.parse("2026-10-31T11:00:00Z"), PayrollRules.windowStart(LocalDate.of(2026, 11, 1), AKL));
        assertEquals(Instant.parse("2026-11-30T11:00:00Z"), PayrollRules.windowEnd(LocalDate.of(2026, 11, 30), AKL));
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), PayrollRules.windowStart(LocalDate.of(2026, 11, 1), UTC));
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), PayrollRules.windowEnd(LocalDate.of(2026, 11, 30), UTC));
    }

    @Test
    void aShiftBelongsToTheDayItStartedOnInTheCompanyZone() {
        Instant start = Instant.parse("2026-11-30T21:30:00Z"); // 23:30 on the 30th in SA, 10:30 on 1 Dec in Auckland
        assertEquals(LocalDate.of(2026, 11, 30), PayrollRules.shiftDate(start, ZoneId.of("Africa/Johannesburg")));
        assertEquals(LocalDate.of(2026, 12, 1), PayrollRules.shiftDate(start, AKL));
        assertEquals(LocalDate.of(2026, 11, 30), PayrollRules.shiftDate(start, UTC));
    }

    @Test
    void theOldFormsStillMeanSouthAfrica() {
        assertEquals(PayrollRules.windowStart(LocalDate.of(2026, 11, 1), PayrollRules.ZONE), PayrollRules.windowStart(LocalDate.of(2026, 11, 1)));
        assertEquals(PayrollRules.windowEnd(LocalDate.of(2026, 11, 30), PayrollRules.ZONE), PayrollRules.windowEnd(LocalDate.of(2026, 11, 30)));
        assertEquals(PayrollRules.shiftDate(Instant.parse("2026-11-19T22:30:00Z"), PayrollRules.ZONE), PayrollRules.shiftDate(Instant.parse("2026-11-19T22:30:00Z")));
    }

    @Test
    void aMissingZoneIsSouthAfricaNotTheServer() {
        java.util.TimeZone before = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland"));
            Instant start = Instant.parse("2026-11-30T21:30:00Z");
            assertEquals(LocalDate.of(2026, 11, 30), PayrollRules.shiftDate(start, null));
            assertEquals(Instant.parse("2026-10-31T22:00:00Z"), PayrollRules.windowStart(LocalDate.of(2026, 11, 1), null));
            assertEquals(Instant.parse("2026-11-30T22:00:00Z"), PayrollRules.windowEnd(LocalDate.of(2026, 11, 30), null));
        } finally {
            java.util.TimeZone.setDefault(before);
        }
    }

    @Test
    void hoursAreNotAffectedByTheZone() {
        PayrollRules.Split s = PayrollRules.split(Instant.parse("2026-11-20T04:00:00Z"), Instant.parse("2026-11-20T15:30:00Z"), 9);
        assertEquals(new java.math.BigDecimal("9.00"), s.regular());
        assertEquals(new java.math.BigDecimal("2.50"), s.overtime());
    }

    @Test
    void theServiceUsesTheCompanyZoneForTheWindowAndTheCsvDate() throws Exception {
        String s = src("SecurityPayrollService.java");
        assertTrue(s.contains("PayrollRules.windowStart(period.getPeriodStart(), zone)"));
        assertTrue(s.contains("PayrollRules.windowEnd(period.getPeriodEnd(), zone)"));
        assertTrue(s.contains("PayrollRules.shiftDate(li.getShiftStartAt(), csvZone)"));
        assertTrue(s.contains("BusinessCalendar.zoneOf(calendar, tenantId)"));
        assertTrue(s.contains("required = false"));
        assertFalse(s.contains("PayrollRules.windowStart(period.getPeriodStart())"));
        assertFalse(s.contains("PayrollRules.shiftDate(li.getShiftStartAt())"));
    }

    @Test
    void theRulesNoLongerHardCodeTheZone() throws Exception {
        assertTrue(src("PayrollRules.java").contains("ZONE = za.co.handyflow.platform.shared.BusinessTime.DEFAULT_ZONE;"));
    }
}
