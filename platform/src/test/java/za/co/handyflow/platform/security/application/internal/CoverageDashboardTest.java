package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.CoverageAttention.Counts;
import za.co.handyflow.platform.security.application.internal.CoverageAttention.Item;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageDashboardTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Shifts S = new Shifts(0, 0, 0, 0, 0);
    private static final Workforce W = new Workforce(10, 9, 0, 0, 0, 0);
    private static final Incidents I = new Incidents(0, 0, 0, 0);
    private static final Complaints C = new Complaints(0, 0);
    private static final Gate G = new Gate(0, 0, 0);

    private static List<AttentionItem> build(int none, int shortC) { return DashboardAttention.build(0, 0, S, W, I, C, G, none, shortC); }
    private static Instant at(String z) { return Instant.parse(z); }

    // ---- counting ----

    @Test
    void countsOnlyStretchesThatCanStillBeFixed() {
        Counts c = CoverageAttention.count(List.of(
                new Item("UNCOVERED", at("2026-11-20T16:00:00Z")),   // still ahead
                new Item("UNCOVERED", at("2026-11-20T09:00:00Z")),   // already over
                new Item("SHORT", at("2026-11-20T12:00:00Z")),
                new Item("SHORT", at("2026-11-20T10:00:00Z")),       // ended exactly now: over
                new Item("COVERED", null)), NOW);
        assertEquals(1, c.uncovered());
        assertEquals(1, c.shortOfGuards());
    }

    @Test
    void coveredAndGaplessItemsAreNeverCounted() {
        Counts c = CoverageAttention.count(List.of(new Item("COVERED", at("2026-11-21T00:00:00Z")), new Item("SHORT", null)), NOW);
        assertEquals(0, c.uncovered());
        assertEquals(0, c.shortOfGuards());
    }

    @Test
    void emptyListCountsNothing() {
        Counts c = CoverageAttention.count(List.of(), NOW);
        assertEquals(0, c.uncovered() + c.shortOfGuards());
    }

    // ---- dashboard list ----

    @Test
    void nothingListedWhenNothingIsShort() {
        assertTrue(build(0, 0).isEmpty());
    }

    @Test
    void oldSignatureStillWorks() {
        assertTrue(DashboardAttention.build(0, 0, S, W, I, C, G).isEmpty());
    }

    @Test
    void unrosteredPostIsADangerLinkedToCoverage() {
        AttentionItem i = build(2, 0).get(0);
        assertEquals("COVERAGE_NONE", i.code());
        assertEquals("DANGER", i.level());
        assertEquals("2 posts with no one rostered today", i.title());
        assertEquals("coverage", i.section());
        assertEquals(2, i.count());
    }

    @Test
    void shortPostIsAWarningAndWordedInTheSingular() {
        AttentionItem i = build(0, 1).get(0);
        assertEquals("COVERAGE_SHORT", i.code());
        assertEquals("WARNING", i.level());
        assertEquals("1 post short of guards today", i.title());
        assertEquals("coverage", i.section());
    }

    @Test
    void dangerRanksAboveWarning() {
        List<AttentionItem> l = build(1, 5);
        assertEquals("COVERAGE_NONE", l.get(0).code());
        assertEquals("COVERAGE_SHORT", l.get(1).code());
    }

    // ---- wiring (source scans) ----

    private static String read(String f) throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/" + f));
    }

    @Test
    void dashboardCannotBeBrokenByCoverage() throws Exception {
        String s = read("SecurityDashboardService.java");
        assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)"));
        assertTrue(s.contains("catch (RuntimeException e) { return new int[]{0, 0}; }"));
        assertTrue(s.contains("if (coverage == null) return new int[]{0, 0};"));
        assertTrue(s.contains("cov[0], cov[1]"));
    }

    @Test
    void coverageCountsRunInTheirOwnTransaction() throws Exception {
        String s = read("CoverageService.java");
        int i = s.indexOf("public int[] attentionCounts");
        assertTrue(s.substring(i - 150, i).contains("Propagation.REQUIRES_NEW"));
        assertTrue(s.substring(i, i + 300).contains("board(tenantId, null, 1, now)"));
    }
}
