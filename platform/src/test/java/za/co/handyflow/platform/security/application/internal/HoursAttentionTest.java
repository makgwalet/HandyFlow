package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Assessment;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.RestBreach;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.State;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HoursAttentionTest {

    private static final Shifts S = new Shifts(0, 0, 0, 0, 0);
    private static final Workforce W = new Workforce(10, 9, 0, 0, 0, 0);
    private static final Incidents I = new Incidents(0, 0, 0, 0);
    private static final Complaints C = new Complaints(0, 0);
    private static final Gate G = new Gate(0, 0, 0);
    private static final RestBreach RB = new RestBreach(Instant.parse("2026-11-16T10:00:00Z"), Instant.parse("2026-11-16T14:00:00Z"), 4);

    private static Assessment a(State s, boolean overlaps, List<RestBreach> rest) { return new Assessment(40, s, 5, overlaps, rest); }
    private static List<AttentionItem> build(HoursAttention.Counts h) { return DashboardAttention.build(0, 0, S, W, I, C, G, 0, 0, h); }
    private static AttentionItem item(List<AttentionItem> l, String code) { return l.stream().filter(i -> i.code().equals(code)).findFirst().orElse(null); }

    @Test
    void countsEachGuardOnceByTheirWorstProblem() {
        var c = HoursAttention.count(List.of(
                a(State.EXCEEDS, true, List.of(RB)),   // worst is the ceiling, even though also double-booked and short of rest
                a(State.OVER, false, List.of(RB)),     // over, even though also short of rest
                a(State.NEAR, true, List.of()),        // near is not a problem by itself; the overlap is
                a(State.OK, false, List.of(RB)),
                a(State.OK, false, List.of()),
                a(State.NEAR, false, List.of())));
        assertEquals(1, c.exceeds());
        assertEquals(1, c.over());
        assertEquals(2, c.rest());
        assertEquals(4, c.total());
    }

    @Test
    void nobodyFlaggedMeansNothing() {
        assertEquals(HoursAttention.Counts.NONE, HoursAttention.count(List.of()));
        assertEquals(0, HoursAttention.count(List.of(a(State.OK, false, List.of()), a(State.NEAR, false, List.of()))).total());
    }

    @Test
    void ceilingIsADangerOverTheLimitAndRestAreWarnings() {
        var l = build(new HoursAttention.Counts(2, 3, 1));
        assertEquals("DANGER", item(l, "HOURS_EXCEEDS").level());
        assertEquals("WARNING", item(l, "HOURS_OVER").level());
        assertEquals("WARNING", item(l, "REST_BREACH").level());
        assertEquals("2 guards rostered past the weekly ceiling", item(l, "HOURS_EXCEEDS").title());
        assertEquals("3 guards rostered past the ordinary weekly limit", item(l, "HOURS_OVER").title());
        assertEquals("1 guard double-booked or short of rest between shifts", item(l, "REST_BREACH").title());
        assertEquals("coverage", item(l, "HOURS_EXCEEDS").section());
        assertEquals("coverage", item(l, "HOURS_OVER").section());
        assertEquals("coverage", item(l, "REST_BREACH").section());
    }

    @Test
    void zeroCountsAreNotListedAndTheDangerComesFirst() {
        assertTrue(build(HoursAttention.Counts.NONE).isEmpty());
        var l = build(new HoursAttention.Counts(1, 5, 0));
        assertNull(item(l, "REST_BREACH"));
        assertEquals("HOURS_EXCEEDS", l.get(0).code());
        assertEquals("HOURS_OVER", l.get(1).code());
    }

    @Test
    void earlierOverloadsStillWorkAndAddNothing() {
        assertTrue(DashboardAttention.build(0, 0, S, W, I, C, G).isEmpty());
        assertTrue(DashboardAttention.build(0, 0, S, W, I, C, G, 0, 0).isEmpty());
        assertEquals("COVERAGE_NONE", DashboardAttention.build(0, 0, S, W, I, C, G, 1, 0).get(0).code());
    }

    // ---- wiring, read from the source ----

    private static String src(String f) throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security/application/internal", f);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + f); return "";
    }

    @Test
    void dashboardUsesHoursOptionallyAndNeverFailsBecauseOfThem() throws Exception {
        String d = src("SecurityDashboardService.java");
        assertTrue(d.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private WeeklyHoursService weeklyHours;"));
        assertTrue(d.contains("if (weeklyHours == null) return HoursAttention.Counts.NONE;"));
        assertTrue(d.contains("catch (RuntimeException e) { return HoursAttention.Counts.NONE; }"));
        assertTrue(d.contains("hoursCounts(tenantId, now)"));
    }

    @Test
    void hoursCountsRunInTheirOwnTransactionAndAreNotCapped() throws Exception {
        String s = src("WeeklyHoursService.java");
        assertTrue(s.contains("propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)\n    public HoursAttention.Counts attentionCounts"));
        int a = s.indexOf("public HoursAttention.Counts attentionCounts");
        String body = s.substring(a, s.indexOf("private record Computed"));
        assertFalse(body.contains("MAX_ROWS"));
        // the company's own zone (chunk 72), never a fixed one
        assertTrue(body.contains("BusinessCalendar.zoneOf(calendar, tenantId)"));
        assertTrue(body.contains("now.atZone(zone)"));
        assertFalse(body.contains("WeeklyHoursRules.SAST"));
    }

    @Test
    void reportCountsFlaggedGuardsNotAllRows() throws Exception {
        String s = src("WeeklyHoursService.java");
        assertTrue(s.contains("filter(Row::flagged).count()"));
        assertFalse(s.contains("c.rows().size(), rows.size()"));
    }
}
