package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Cover;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Post;
import za.co.handyflow.platform.security.application.internal.MonthlyPackRules.CoverFigures;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyPackRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static String code(Runnable r) { try { r.run(); return "none"; } catch (HandyFlowException e) { return e.getErrorCode(); } }
    private static Post post(Set<Integer> days, String s, String e, int need) {
        return new Post(UUID.randomUUID(), UUID.randomUUID(), "Gate", days, LocalTime.parse(s), LocalTime.parse(e), need);
    }
    private static Cover cover(String a, String b) { return new Cover(Instant.parse(a), Instant.parse(b)); }

    @Test
    void defaultsToLastMonthAndNeverAFutureOne() {
        assertEquals(YearMonth.of(2026, 10), MonthlyPackRules.month(null, NOW));
        assertEquals(YearMonth.of(2026, 10), MonthlyPackRules.month("  ", NOW));
        assertEquals(YearMonth.of(2026, 11), MonthlyPackRules.month("2026-11", NOW));
        assertEquals(YearMonth.of(2025, 1), MonthlyPackRules.month("2025-01", NOW));
        assertEquals("PACK_FUTURE_MONTH", code(() -> MonthlyPackRules.month("2026-12", NOW)));
        assertEquals("PACK_MONTH_INVALID", code(() -> MonthlyPackRules.month("Oct 2026", NOW)));
        assertEquals("PACK_MONTH_INVALID", code(() -> MonthlyPackRules.month("2026-13", NOW)));
    }

    @Test
    void januaryDefaultsToDecemberOfThePreviousYear() {
        assertEquals(YearMonth.of(2025, 12), MonthlyPackRules.month(null, Instant.parse("2026-01-10T10:00:00Z")));
    }

    @Test
    void currentMonthUsesSouthAfricanTimeAtTheBoundary() {
        // 23:30 UTC on 30 Nov is already 1 Dec in Johannesburg
        assertEquals(YearMonth.of(2026, 11), MonthlyPackRules.month(null, Instant.parse("2026-11-30T23:30:00Z")));
        assertEquals(YearMonth.of(2026, 12), MonthlyPackRules.month("2026-12", Instant.parse("2026-11-30T23:30:00Z")));
    }

    @Test
    void monthWindowIsSouthAfrican() {
        Instant[] w = MonthlyPackRules.window(YearMonth.of(2026, 10));
        assertEquals(Instant.parse("2026-09-30T22:00:00Z"), w[0]);
        assertEquals(Instant.parse("2026-10-31T22:00:00Z"), w[1]);
    }

    @Test
    void inProgressOnlyForTheCurrentMonth() {
        assertTrue(MonthlyPackRules.inProgress(YearMonth.of(2026, 11), NOW));
        assertFalse(MonthlyPackRules.inProgress(YearMonth.of(2026, 10), NOW));
    }

    @Test
    void periodDays() {
        assertEquals(31, MonthlyPackRules.daysInPeriod(YearMonth.of(2026, 10), NOW));
        assertEquals(21, MonthlyPackRules.daysInPeriod(YearMonth.of(2026, 11), NOW));
        assertEquals(28, MonthlyPackRules.daysInPeriod(YearMonth.of(2026, 2), NOW));
    }

    @Test
    void percentages() {
        assertNull(MonthlyPackRules.pct(0, 0));
        assertNull(MonthlyPackRules.pct(5, 0));
        assertEquals(50.0, MonthlyPackRules.pct(1, 2));
        assertEquals(66.7, MonthlyPackRules.pct(2, 3));
        assertEquals(100.0, MonthlyPackRules.pct(12, 12));
    }

    // Oct 2026: Mondays are 5, 12, 19, 26. A Monday day post 06:00-18:00 = 12 h each.
    private static final Post MONDAYS = post(Set.of(1), "06:00", "18:00", 1);
    private static final Instant END_OF_OCT = Instant.parse("2026-10-31T22:00:00Z");

    @Test
    void requiredHoursAreWindowHoursTimesGuardsNeeded() {
        CoverFigures f = MonthlyPackRules.cover(List.of(post(Set.of(1), "06:00", "18:00", 2)), YearMonth.of(2026, 10), END_OF_OCT, List.of());
        assertEquals(4, f.windows());
        assertEquals(96.0, f.requiredHours());   // 4 x 12 h x 2 guards
        assertEquals(0.0, f.coveredHours());
        assertEquals(4, f.windowsUncovered());
    }

    @Test
    void fullCoverIsOneHundredPercent() {
        List<Cover> c = List.of(cover("2026-10-05T04:00:00Z", "2026-10-05T16:00:00Z"), cover("2026-10-12T04:00:00Z", "2026-10-12T16:00:00Z"),
                cover("2026-10-19T04:00:00Z", "2026-10-19T16:00:00Z"), cover("2026-10-26T04:00:00Z", "2026-10-26T16:00:00Z"));
        CoverFigures f = MonthlyPackRules.cover(List.of(MONDAYS), YearMonth.of(2026, 10), END_OF_OCT, c);
        assertEquals(48.0, f.requiredHours());
        assertEquals(48.0, f.coveredHours());
        assertEquals(0, f.windowsShort() + f.windowsUncovered());
    }

    @Test
    void aMissingHourReducesCoveredHoursByExactlyThatHour() {
        List<Cover> c = List.of(cover("2026-10-05T05:00:00Z", "2026-10-05T16:00:00Z"), cover("2026-10-12T04:00:00Z", "2026-10-12T16:00:00Z"),
                cover("2026-10-19T04:00:00Z", "2026-10-19T16:00:00Z"), cover("2026-10-26T04:00:00Z", "2026-10-26T16:00:00Z"));
        CoverFigures f = MonthlyPackRules.cover(List.of(MONDAYS), YearMonth.of(2026, 10), END_OF_OCT, c);
        assertEquals(47.0, f.coveredHours());
        assertEquals(1, f.windowsShort());
        assertEquals(0, f.windowsUncovered());
    }

    @Test
    void oneOfTwoGuardsGivesHalfTheHours() {
        Post two = post(Set.of(1), "06:00", "18:00", 2);
        List<Cover> c = List.of(cover("2026-10-05T04:00:00Z", "2026-10-05T16:00:00Z"));
        CoverFigures f = MonthlyPackRules.cover(List.of(two), YearMonth.of(2026, 10), END_OF_OCT, c);
        assertEquals(96.0, f.requiredHours());
        // first Monday: 12 of 24 guard-hours covered; other three Mondays: none
        assertEquals(12.0, f.coveredHours());
    }

    @Test
    void extraGuardsDoNotMakeUpForGapsElsewhere() {
        Post p = post(Set.of(1), "06:00", "18:00", 1);
        List<Cover> c = List.of(cover("2026-10-05T04:00:00Z", "2026-10-05T16:00:00Z"), cover("2026-10-05T04:00:00Z", "2026-10-05T16:00:00Z"));
        CoverFigures f = MonthlyPackRules.cover(List.of(p), YearMonth.of(2026, 10), END_OF_OCT, c);
        assertEquals(12.0, f.coveredHours());  // not 24
    }

    @Test
    void currentMonthOnlyCountsFinishedPostDays() {
        // Nov 2026 Mondays: 2, 9, 16, 23, 30. Until 21 Nov 10:00Z only the first three have finished.
        CoverFigures f = MonthlyPackRules.cover(List.of(MONDAYS), YearMonth.of(2026, 11), NOW, List.of());
        assertEquals(3, f.windows());
        assertEquals(36.0, f.requiredHours());
    }

    @Test
    void noPostsMeansNothingRequired() {
        CoverFigures f = MonthlyPackRules.cover(List.of(), YearMonth.of(2026, 10), END_OF_OCT, List.of());
        assertEquals(0, f.windows());
        assertNull(MonthlyPackRules.pct(f.coveredHours(), f.requiredHours()));
    }

    // ---- source scans ----

    private static String svc() throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/MonthlyPackService.java"));
    }

    @Test
    void reportCarriesNoPersonalDetail() throws Exception {
        String s = svc();
        for (String f : new String[]{"person_name", "id_number", "driver_name", "phone", "first_name", "last_name", "description", "photo"}) {
            assertFalse(s.contains(f), "the client report must not read " + f);
        }
    }

    @Test
    void everyQueryIsTenantAndSiteScoped() throws Exception {
        String s = svc();
        int tenantScoped = s.split("tenant_id = \\? AND site_id = \\?", -1).length - 1;
        assertTrue(tenantScoped >= 8, "found " + tenantScoped);
        assertTrue(s.contains("id = ? AND tenant_id = ? AND deleted_at IS NULL"));
    }

    @Test
    void coverCountsOnlyRealShiftsAndDeletedIncidentsAreLeftOut() throws Exception {
        String s = svc();
        assertTrue(s.contains("status IN ('SCHEDULED','ACTIVE','COMPLETED')"));
        int i = s.indexOf("FROM security_incidents");
        assertTrue(s.substring(i, i + 160).contains("deleted_at IS NULL"));
    }

    @Test
    void endpointNeedsReadPermission() throws Exception {
        String c = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/api/MonthlyPackController.java"));
        int i = c.indexOf("monthly-pack");
        assertTrue(c.substring(i, i + 120).contains("SECURITY_READ"));
    }
}
