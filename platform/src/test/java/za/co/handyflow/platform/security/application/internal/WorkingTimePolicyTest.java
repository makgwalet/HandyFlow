package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.*;
import za.co.handyflow.platform.security.application.internal.WorkingTimePolicy.Policy;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkingTimePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static Policy p(String cat, LocalDate from, double warn, double ord, double ot, double daily, double dailyOt, double rest, String basis) {
        return new Policy(null, cat, from, warn, ord, ot, daily, dailyOt, rest, basis);
    }
    private static Policy general() { return p("default", TODAY, 40, 45, 10, 12, 3, 12, null); }
    private static String code(Policy x) {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> WorkingTimePolicy.validate(x, TODAY));
        return e.getErrorCode();
    }

    @Test
    void theGeneralFiguresAreAcceptedWithoutALegalBasisAndTheCeilingIsDerived() {
        Policy v = WorkingTimePolicy.validate(general(), TODAY);
        assertEquals("DEFAULT", v.category());
        assertEquals(55.0, v.ceiling());
        assertNull(v.legalBasis());
        assertEquals(new Thresholds(40, 45, 55, 12, 12), v.thresholds());
    }

    @Test
    void anythingBeyondTheGeneralFiguresNeedsALegalBasis() {
        assertEquals("LEGAL_BASIS_REQUIRED", code(p("A", TODAY, 40, 48, 10, 12, 3, 12, null)));   // ordinary
        assertEquals("LEGAL_BASIS_REQUIRED", code(p("A", TODAY, 40, 45, 15, 12, 3, 12, null)));   // overtime
        assertEquals("LEGAL_BASIS_REQUIRED", code(p("A", TODAY, 40, 45, 10, 13, 3, 12, null)));   // daily
        assertEquals("LEGAL_BASIS_REQUIRED", code(p("A", TODAY, 40, 45, 10, 12, 4, 12, null)));   // daily overtime
        assertEquals("LEGAL_BASIS_REQUIRED", code(p("A", TODAY, 40, 45, 10, 12, 3, 10, null)));   // rest
        assertEquals("LEGAL_BASIS_REQUIRED", code(p("A", TODAY, 40, 45, 15, 12, 3, 12, "  short ")));
        Policy ok = WorkingTimePolicy.validate(p("A", TODAY, 40, 45, 15, 12, 3, 12, "  Collective agreement 2026/03, clause 7  "), TODAY);
        assertEquals("Collective agreement 2026/03, clause 7", ok.legalBasis());
        assertEquals(60.0, ok.ceiling());
    }

    @Test
    void stricterThanGeneralNeedsNoBasis() {
        assertDoesNotThrow(() -> WorkingTimePolicy.validate(p("A", TODAY, 30, 40, 5, 10, 2, 14, null), TODAY));
    }

    @Test
    void aPolicyCannotStartInThePast() {
        assertEquals("EFFECTIVE_DATE_PAST", code(p("A", TODAY.minusDays(1), 40, 45, 10, 12, 3, 12, null)));
        assertDoesNotThrow(() -> WorkingTimePolicy.validate(p("A", TODAY, 40, 45, 10, 12, 3, 12, null), TODAY));
        assertEquals("INVALID_EFFECTIVE_DATE", code(p("A", null, 40, 45, 10, 12, 3, 12, null)));
    }

    @Test
    void nonsenseHoursAreRefused() {
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 50, 45, 10, 12, 3, 12, null)));            // warn above ordinary
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 0, 45, 10, 12, 3, 12, null)));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 0, 10, 12, 3, 12, null)));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 90, 10, 12, 3, 12, "x".repeat(20))));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 45, -1, 12, 3, 12, null)));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 45, 41, 12, 3, 12, "x".repeat(20))));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 45, 10, 25, 3, 12, "x".repeat(20))));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 45, 10, 12, 13, 12, "x".repeat(20)))); // daily overtime above daily
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, 45, 10, 12, 3, 25, null)));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, Double.NaN, 45, 10, 12, 3, 12, null)));
        assertEquals("INVALID_HOURS", code(p("A", TODAY, 40, Double.POSITIVE_INFINITY, 10, 12, 3, 12, null)));
        assertEquals("INVALID_LEGAL_BASIS", code(p("A", TODAY, 40, 45, 10, 12, 3, 12, "x".repeat(501))));
    }

    @Test
    void categoriesAreNormalisedAndChecked() {
        assertEquals("NIGHT_SHIFT", WorkingTimePolicy.category(" night shift "));
        assertEquals("DEFAULT", WorkingTimePolicy.category("default"));
        for (String bad : new String[]{null, "", "  ", "a-b", "a;b", "x".repeat(41), "naïve"})
            assertThrows(HandyFlowException.class, () -> WorkingTimePolicy.category(bad), String.valueOf(bad));
    }

    private static final Policy FALLBACK = WorkingTimePolicy.fallback(Thresholds.DEFAULTS, 3);

    @Test
    void theFallbackIsTheConfiguredFigures() {
        assertEquals(45.0, FALLBACK.ordinaryWeekly());
        assertEquals(10.0, FALLBACK.maxOvertimeWeekly());
        assertEquals(12.0, FALLBACK.maxDaily());
        assertEquals(Thresholds.DEFAULTS, FALLBACK.thresholds());
    }

    @Test
    void theLatestPolicyThatHasStartedApplies() {
        Policy old = p("DEFAULT", LocalDate.of(2026, 1, 1), 40, 45, 10, 12, 3, 12, null);
        Policy newer = p("DEFAULT", LocalDate.of(2026, 12, 1), 35, 40, 10, 12, 3, 12, null);
        List<Policy> all = List.of(newer, old);
        assertSame(old, WorkingTimePolicy.select(all, "DEFAULT", LocalDate.of(2026, 11, 30), FALLBACK));
        assertSame(newer, WorkingTimePolicy.select(all, "DEFAULT", LocalDate.of(2026, 12, 1), FALLBACK));
        assertSame(newer, WorkingTimePolicy.select(all, "DEFAULT", LocalDate.of(2027, 3, 1), FALLBACK));
    }

    @Test
    void aPolicyDatedMidWeekFirstAppliesTheFollowingWeek() {
        Policy wed = p("DEFAULT", LocalDate.of(2026, 11, 25), 35, 40, 10, 12, 3, 12, null);
        LocalDate monday = LocalDate.of(2026, 11, 23);
        assertSame(FALLBACK, WorkingTimePolicy.select(List.of(wed), "DEFAULT", monday, FALLBACK));
        assertSame(wed, WorkingTimePolicy.select(List.of(wed), "DEFAULT", monday.plusDays(7), FALLBACK));
    }

    @Test
    void aCategoryFallsBackToTheCompanyDefaultThenTheConfiguredFigures() {
        Policy def = p("DEFAULT", LocalDate.of(2026, 1, 1), 40, 45, 10, 12, 3, 12, null);
        Policy night = p("NIGHT", LocalDate.of(2026, 1, 1), 40, 45, 10, 12, 3, 12, "Agreement 7, clause 4");
        LocalDate week = LocalDate.of(2026, 11, 16);
        assertSame(night, WorkingTimePolicy.select(List.of(def, night), "NIGHT", week, FALLBACK));
        assertSame(def, WorkingTimePolicy.select(List.of(def, night), "OTHER", week, FALLBACK));
        assertSame(def, WorkingTimePolicy.select(List.of(def), "NIGHT", week, FALLBACK));
        assertSame(FALLBACK, WorkingTimePolicy.select(List.of(night), "OTHER", week, FALLBACK));
        assertSame(FALLBACK, WorkingTimePolicy.select(List.of(), "DEFAULT", week, FALLBACK));
    }

    @Test
    void aCategoryStartingLaterDoesNotHideTheDefaultMeanwhile() {
        Policy def = p("DEFAULT", LocalDate.of(2026, 1, 1), 40, 45, 10, 12, 3, 12, null);
        Policy nightLater = p("NIGHT", LocalDate.of(2027, 1, 1), 40, 45, 10, 12, 3, 12, "Agreement 7, clause 4");
        assertSame(def, WorkingTimePolicy.select(List.of(def, nightLater), "NIGHT", LocalDate.of(2026, 11, 16), FALLBACK));
    }

    // ---- daily limit -------------------------------------------------------------------------------------------------------------

    private static final Instant WS = Instant.parse("2026-11-15T22:00:00Z");
    private static final Instant WE = Instant.parse("2026-11-22T22:00:00Z");
    private static Slot slot(String a, String b) { return new Slot(Instant.parse(a), Instant.parse(b)); }
    private static Assessment assess(Thresholds t, Slot... s) { return WeeklyHoursRules.assess(List.of(s), WS, WE, t); }

    @Test
    void aDayOverTheDailyLimitIsFlaggedAndTwelveExactlyIsNot() {
        Assessment twelve = assess(Thresholds.DEFAULTS, slot("2026-11-16T04:00:00Z", "2026-11-16T16:00:00Z"));
        assertTrue(twelve.dayBreaches().isEmpty());
        assertFalse(twelve.flagged());
        Assessment thirteen = assess(Thresholds.DEFAULTS, slot("2026-11-16T04:00:00Z", "2026-11-16T17:00:00Z"));
        assertEquals(1, thirteen.dayBreaches().size());
        assertEquals(LocalDate.of(2026, 11, 16), thirteen.dayBreaches().get(0).day());
        assertEquals(13.0, thirteen.dayBreaches().get(0).hours());
        assertTrue(thirteen.flagged());
    }

    @Test
    void anOvernightShiftIsSplitAtSouthAfricanMidnight() {
        // 18:00 to 06:00 SAST = 16:00Z to 04:00Z: 6 h on Monday, 6 h on Tuesday, neither over 12
        Assessment a = assess(Thresholds.DEFAULTS, slot("2026-11-16T16:00:00Z", "2026-11-17T04:00:00Z"));
        assertTrue(a.dayBreaches().isEmpty());
        // a 14 h night shift from 16:00 SAST: 8 h Monday, 6 h Tuesday: still no single day over 12
        assertTrue(assess(Thresholds.DEFAULTS, slot("2026-11-16T14:00:00Z", "2026-11-17T04:00:00Z")).dayBreaches().isEmpty());
        // two shifts the same SAST day that together pass 12
        Assessment two = assess(Thresholds.DEFAULTS, slot("2026-11-16T04:00:00Z", "2026-11-16T11:00:00Z"), slot("2026-11-16T12:00:00Z", "2026-11-16T18:00:00Z"));
        assertEquals(1, two.dayBreaches().size());
        assertEquals(13.0, two.dayBreaches().get(0).hours());
    }

    @Test
    void aLowerOrMissingDailyLimitIsRespected() {
        Thresholds eight = new Thresholds(40, 45, 55, 12, 8);
        assertEquals(1, assess(eight, slot("2026-11-16T04:00:00Z", "2026-11-16T13:00:00Z")).dayBreaches().size());
        assertTrue(assess(new Thresholds(40, 45, 55, 12), slot("2026-11-16T00:00:00Z", "2026-11-16T22:00:00Z")).dayBreaches().isEmpty());
    }

    @Test
    void aDoubleBookedDayIsNotCountedTwiceTowardsTheDailyLimit() {
        Assessment a = assess(Thresholds.DEFAULTS, slot("2026-11-16T04:00:00Z", "2026-11-16T14:00:00Z"), slot("2026-11-16T04:00:00Z", "2026-11-16T14:00:00Z"));
        assertTrue(a.dayBreaches().isEmpty());
        assertTrue(a.overlaps());
    }

    @Test
    void aDayBreachCountsOnTheDashboardAsAnAttentionItem() {
        Assessment a = new Assessment(30, State.OK, 3, false, List.of(), List.of(new DayBreach(LocalDate.of(2026, 11, 16), 13)));
        assertEquals(1, HoursAttention.count(List.of(a)).rest());
        assertEquals(0, HoursAttention.count(List.of(new Assessment(30, State.OK, 3, false, List.of(), List.of()))).total());
    }

    // ---- wiring ------------------------------------------------------------------------------------------------------------------

    private static String src(String rel) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + rel)); }

    @Test
    void theWeeklyReportUsesEachGuardsCategoryPolicy() throws Exception {
        String s = src("application/internal/WeeklyHoursService.java");
        assertTrue(s.contains("policies.startedBy(tenantId, monday)"));
        assertTrue(s.contains("policies.categories(tenantId)"));
        assertTrue(s.contains("WorkingTimePolicy.select(inForce, category, monday, fallback)"));
        assertTrue(s.contains("policy.thresholds(), zone)"));
        assertFalse(s.contains("this.thresholds"));
        assertTrue(s.contains("${handyflow.security.hours.max-daily:12}"));
    }

    @Test
    void policyStorageIsTenantScopedAppendOnlyAndAudited() throws Exception {
        String s = src("application/internal/WorkingTimePolicyService.java");
        assertTrue(s.contains("WHERE tenant_id = ? ORDER BY category"));
        assertTrue(s.contains("WHERE tenant_id = ? AND effective_from <= ?"));
        assertTrue(s.contains("FROM security_guard_work_category WHERE tenant_id = ?\""));
        assertFalse(s.contains("UPDATE security_working_time_policies"));
        assertFalse(s.contains("DELETE FROM security_working_time_policies"));
        assertTrue(s.contains("WorkingTimePolicy.validate(in, za.co.handyflow.platform.shared.BusinessCalendar.todayOf(calendar, tenantId))"));
        assertTrue(s.contains("\"POLICY_ADDED\""));
        assertTrue(s.contains("\"WORK_CATEGORY_SET\""));
        assertTrue(s.contains("security_guards WHERE id = ? AND tenant_id = ?"));
        assertTrue(s.contains("\"UNKNOWN_CATEGORY\""));
        assertTrue(s.contains("WHERE security_guard_work_category.tenant_id = EXCLUDED.tenant_id"));
    }

    @Test
    void onlyManagersChangeThemAndTheFeatureGuardIsOn() throws Exception {
        String c = src("api/WorkingTimePolicyController.java");
        for (String[] m : new String[][]{{"@GetMapping(\"/api/v1/security/working-time-policies\")", "SECURITY_READ"},
                {"@PostMapping(\"/api/v1/security/working-time-policies\")", "SECURITY_MANAGE"},
                {"@PutMapping(\"/api/v1/security/guards/{guardId}/working-time-category\")", "SECURITY_MANAGE"}}) {
            int i = c.indexOf(m[0]);
            assertTrue(i >= 0, m[0]);
            assertTrue(c.substring(i, i + 160).contains(m[1]), m[0]);
        }
        assertEquals(3, c.split("featureGuard.requireModule\\(\"security\"\\)", -1).length - 1);
    }

    @Test
    void theMigrationIsAppendOnlyAndRangeChecked() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V860__security_working_time_policies.sql"));
        assertTrue(m.contains("UNIQUE (tenant_id, category, effective_from)"));
        assertTrue(m.contains("BEFORE UPDATE OR DELETE ON security_working_time_policies"));
        assertTrue(m.contains("chk_wtp_ranges"));
        assertTrue(m.contains("PRIMARY KEY") && m.contains("security_guard_work_category"));
    }
}
