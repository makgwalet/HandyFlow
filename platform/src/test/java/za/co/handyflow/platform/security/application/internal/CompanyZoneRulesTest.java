package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Post;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Slot;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The weekly-hours, monthly-pack and coverage rules read days in the company's zone; the old signatures still mean South Africa. */
class CompanyZoneRulesTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg"), UTC = ZoneId.of("UTC"), AKL = ZoneId.of("Pacific/Auckland");
    private static Instant at(String s) { return Instant.parse(s); }

    // ---- weekly hours --------------------------------------------------------------------------------------------------------

    @Test
    void aWeekStartsAtMondayMidnightInTheCompanyZone() {
        LocalDate d = LocalDate.of(2026, 11, 18);
        assertEquals(at("2026-11-15T22:00:00Z"), WeeklyHoursRules.week(d, SAST)[0]);
        assertEquals(at("2026-11-16T00:00:00Z"), WeeklyHoursRules.week(d, UTC)[0]);
        assertEquals(at("2026-11-15T11:00:00Z"), WeeklyHoursRules.week(d, AKL)[0]);
        assertArrayEquals(WeeklyHoursRules.week(d), WeeklyHoursRules.week(d, SAST));
    }

    @Test
    void theDailyLimitIsCutAtMidnightInTheCompanyZone() {
        // 20:00 to 06:00 local in Auckland (UTC+13) = 07:00Z to 17:00Z on 16 Nov: 4 h on Monday and 6 h on Tuesday there, never over 12.
        // The same stretch cut at South African midnight would be 9 h + 1 h; at UTC midnight it is all one day (10 h).
        // A 13 h shift 08:00 to 21:00 Auckland on Monday is over the limit in Auckland.
        Instant[] w = WeeklyHoursRules.week(LocalDate.of(2026, 11, 16), AKL);
        List<Slot> thirteen = List.of(new Slot(at("2026-11-15T19:00:00Z"), at("2026-11-16T08:00:00Z")));
        WeeklyHoursRules.Assessment inAkl = WeeklyHoursRules.assess(thirteen, w[0], w[1], WeeklyHoursRules.Thresholds.DEFAULTS, AKL);
        assertEquals(1, inAkl.dayBreaches().size());
        assertEquals(LocalDate.of(2026, 11, 16), inAkl.dayBreaches().get(0).day());
        assertEquals(13.0, inAkl.dayBreaches().get(0).hours());
        // Same slot judged as South African days: split 2026-11-15T19:00Z..22:00Z (Sun) and 22:00Z..08:00Z (Mon) — no single day passes 12 h
        Instant[] ws = WeeklyHoursRules.week(LocalDate.of(2026, 11, 16), SAST);
        assertTrue(WeeklyHoursRules.assess(thirteen, ws[0], ws[1], WeeklyHoursRules.Thresholds.DEFAULTS, SAST).dayBreaches().isEmpty());
    }

    @Test
    void aLongDayJustBeforeTheWeekIsNotFlaggedAsPartOfIt() {
        // Sunday 08:00 to 21:00 Auckland time (13 h) belongs to the week before; it is only in the data for the rest check.
        Instant[] w = WeeklyHoursRules.week(LocalDate.of(2026, 11, 16), AKL);
        List<Slot> before = List.of(new Slot(at("2026-11-14T19:00:00Z"), at("2026-11-15T08:00:00Z")));
        assertTrue(WeeklyHoursRules.assess(before, w[0], w[1], WeeklyHoursRules.Thresholds.DEFAULTS, AKL).dayBreaches().isEmpty());
    }

    @Test
    void theOldAssessSignatureStillMeansSouthAfrica() {
        Instant[] w = WeeklyHoursRules.week(LocalDate.of(2026, 11, 16));
        List<Slot> s = List.of(new Slot(at("2026-11-16T04:00:00Z"), at("2026-11-16T17:00:00Z")));
        assertEquals(WeeklyHoursRules.assess(s, w[0], w[1], WeeklyHoursRules.Thresholds.DEFAULTS, SAST), WeeklyHoursRules.assess(s, w[0], w[1], WeeklyHoursRules.Thresholds.DEFAULTS));
    }

    // ---- monthly pack --------------------------------------------------------------------------------------------------------

    @Test
    void theMonthJustEndedDependsOnTheCompanyZone() {
        Instant now = at("2026-11-30T23:00:00Z"); // 1 Dec 01:00 in South Africa, still 30 Nov in UTC
        assertEquals(YearMonth.of(2026, 11), MonthlyPackRules.month(null, now, SAST));
        assertEquals(YearMonth.of(2026, 10), MonthlyPackRules.month(null, now, UTC));
        assertEquals(MonthlyPackRules.month(null, now), MonthlyPackRules.month(null, now, SAST));
        assertThrows(HandyFlowException.class, () -> MonthlyPackRules.month("2026-12", now, UTC));
        assertEquals(YearMonth.of(2026, 12), MonthlyPackRules.month("2026-12", now, SAST));
    }

    @Test
    void aMonthWindowStartsAtLocalMidnight() {
        assertEquals(at("2026-10-31T22:00:00Z"), MonthlyPackRules.window(YearMonth.of(2026, 11), SAST)[0]);
        assertEquals(at("2026-10-31T11:00:00Z"), MonthlyPackRules.window(YearMonth.of(2026, 11), AKL)[0]);
        assertArrayEquals(MonthlyPackRules.window(YearMonth.of(2026, 11)), MonthlyPackRules.window(YearMonth.of(2026, 11), SAST));
    }

    @Test
    void inProgressAndDaysInPeriodFollowTheCompanyDay() {
        Instant now = at("2026-11-30T23:00:00Z");
        YearMonth nov = YearMonth.of(2026, 11);
        assertFalse(MonthlyPackRules.inProgress(nov, now, SAST));
        assertTrue(MonthlyPackRules.inProgress(nov, now, UTC));
        assertEquals(30, MonthlyPackRules.daysInPeriod(nov, now, UTC));
        assertEquals(30, MonthlyPackRules.daysInPeriod(nov, now, SAST)); // a finished month counts all its days
        assertEquals(1, MonthlyPackRules.daysInPeriod(YearMonth.of(2026, 12), now, SAST));
        assertEquals(MonthlyPackRules.inProgress(nov, now), MonthlyPackRules.inProgress(nov, now, SAST));
    }

    @Test
    void aMonthIsFinishedForEmailOnlyOnceItEndedInTheCompanyZone() {
        Instant now = at("2026-10-31T23:00:00Z"); // 1 Nov 01:00 in South Africa, 31 Oct in UTC
        YearMonth oct = YearMonth.of(2026, 10);
        assertDoesNotThrow(() -> ReportEmailRules.requireFinished(oct, now, SAST));
        assertThrows(HandyFlowException.class, () -> ReportEmailRules.requireFinished(oct, now, UTC));
        assertDoesNotThrow(() -> ReportEmailRules.requireFinished(oct, now));
    }

    // ---- coverage ------------------------------------------------------------------------------------------------------------

    private static final Post NIGHT = new Post(UUID.randomUUID(), UUID.randomUUID(), "Gate", Set.of(1, 2, 3, 4, 5, 6, 7), LocalTime.of(18, 0), LocalTime.of(6, 0), 1);

    @Test
    void aPostsClockTimesAreReadInTheCompanyZone() {
        LocalDate d = LocalDate.of(2026, 11, 16);
        var sast = CoverageRules.windows(NIGHT, d, 1, SAST).get(0);
        var akl = CoverageRules.windows(NIGHT, d, 1, AKL).get(0);
        assertEquals(at("2026-11-16T16:00:00Z"), sast.start());
        assertEquals(at("2026-11-17T04:00:00Z"), sast.end());
        assertEquals(at("2026-11-16T05:00:00Z"), akl.start());
        assertEquals(at("2026-11-16T17:00:00Z"), akl.end());
        assertEquals(CoverageRules.windows(NIGHT, d, 1).get(0), sast);
    }

    @Test
    void coverFiguresUseTheCompanyZonesWindows() {
        YearMonth m = YearMonth.of(2026, 11);
        Instant until = at("2026-12-01T00:00:00Z");
        // roster covers exactly the South African night every day of the month
        List<CoverageRules.Cover> covers = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            Instant s = LocalDate.of(2026, 11, i).atTime(18, 0).atZone(SAST).toInstant();
            covers.add(new CoverageRules.Cover(s, s.plusSeconds(12 * 3600)));
        }
        var sast = MonthlyPackRules.cover(List.of(NIGHT), m, until, covers, SAST);
        assertEquals(100.0, MonthlyPackRules.pct(sast.coveredHours(), sast.requiredHours()));
        var akl = MonthlyPackRules.cover(List.of(NIGHT), m, until, covers, AKL);
        assertTrue(akl.coveredHours() < akl.requiredHours(), "the same roster does not cover an Auckland night");
        assertEquals(MonthlyPackRules.cover(List.of(NIGHT), m, until, covers).coveredHours(), sast.coveredHours());
    }

    // ---- wiring --------------------------------------------------------------------------------------------------------------

    private static String src(String f) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/" + f)); }
    private static final String BC = "za.co.handyflow.platform.shared.BusinessCalendar";

    @Test
    void theServicesReadTheCompanyZoneAndStayConstructibleByHand() throws Exception {
        for (String f : new String[]{"WeeklyHoursService", "MonthlyPackService", "CoverageService", "SecurityDashboardService", "MonthlyPackEmailService", "WorkingTimePolicyService"}) {
            String s = src(f + ".java");
            assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private " + BC + " calendar;"), f);
            assertTrue(s.contains(BC + ".zoneOf(calendar, tenantId)") || s.contains(BC + ".todayOf(calendar, tenantId)"), f);
            assertFalse(s.contains("SAST"), f + " must not name the fixed zone");
            assertFalse(s.contains("ZoneId.of(\"Africa/Johannesburg\")"), f);
        }
    }

    @Test
    void theServicesCallTheZoneAwareRules() throws Exception {
        String w = src("WeeklyHoursService.java");
        assertTrue(w.contains("WeeklyHoursRules.week(day, zone)"));
        assertTrue(w.contains("WeeklyHoursRules.week(now.atZone(zone).toLocalDate(), zone)"));
        assertTrue(w.contains("policy.thresholds(), zone)"));
        String m = src("MonthlyPackService.java");
        assertTrue(m.contains("MonthlyPackRules.month(monthAsked, now, zone)"));
        assertTrue(m.contains("MonthlyPackRules.window(m, zone)"));
        assertTrue(m.contains("MonthlyPackRules.cover(posts, m, until, covers, zone)"));
        assertTrue(m.contains("MonthlyPackRules.inProgress(m, now, zone)"));
        assertTrue(m.contains("MonthlyPackRules.daysInPeriod(m, now, zone)"));
        String c = src("CoverageService.java");
        assertTrue(c.contains("from.atStartOfDay(zone).toInstant()"));
        assertTrue(c.contains("covers.getOrDefault(e.getKey(), List.of()), zone)"));
        String d = src("SecurityDashboardService.java");
        assertTrue(d.contains("today.atStartOfDay(zone).toInstant()"));
        assertTrue(d.contains("dayStartTz(today, zone)"));
        assertTrue(src("MonthlyPackEmailService.java").contains("ReportEmailRules.requireFinished(YearMonth.parse(p.month()), now, " + BC + ".zoneOf(calendar, tenantId))"));
    }

    @Test
    void theRulesNoLongerHardCodeTheZone() throws Exception {
        for (String f : new String[]{"WeeklyHoursRules", "CoverageRules", "OccurrenceBookRules"})
            assertTrue(src(f + ".java").contains("SAST = za.co.handyflow.platform.shared.BusinessTime.DEFAULT_ZONE;"), f);
    }
}
