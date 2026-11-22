package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.CoverageRules.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoverageRulesTest {

    // 2026-11-20 is a Friday (ISO 5). South Africa is UTC+2.
    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
    private static Post post(Set<Integer> days, String s, String e, int need) {
        return new Post(UUID.randomUUID(), UUID.randomUUID(), "Main gate", days, LocalTime.parse(s), LocalTime.parse(e), need);
    }
    private static Instant t(String z) { return Instant.parse(z); }
    private static Cover cover(String a, String b) { return new Cover(t(a), t(b)); }
    private static String code(Runnable r) { try { r.run(); return "none"; } catch (HandyFlowException e) { return e.getErrorCode(); } }

    @Test
    void dayPostIsInSouthAfricanTime() {
        List<Window> w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 1), FRI, 1);
        assertEquals(1, w.size());
        assertEquals(t("2026-11-20T04:00:00Z"), w.get(0).start());
        assertEquals(t("2026-11-20T16:00:00Z"), w.get(0).end());
    }

    @Test
    void nightPostRunsPastMidnight() {
        Window w = CoverageRules.windows(post(Set.of(5), "18:00", "06:00", 1), FRI, 1).get(0);
        assertEquals(t("2026-11-20T16:00:00Z"), w.start());
        assertEquals(t("2026-11-21T04:00:00Z"), w.end());
    }

    @Test
    void equalTimesMeanTwentyFourHours() {
        Window w = CoverageRules.windows(post(Set.of(5), "00:00", "00:00", 1), FRI, 1).get(0);
        assertEquals(24L * 3600, java.time.Duration.between(w.start(), w.end()).getSeconds());
    }

    @Test
    void onlyChosenWeekdaysAreWindows() {
        // Fri..Thu: weekdays Mon(1) and Fri(5) -> Fri 20th and Mon 23rd
        List<Window> w = CoverageRules.windows(post(Set.of(1, 5), "06:00", "18:00", 1), FRI, 7);
        assertEquals(List.of(FRI, LocalDate.of(2026, 11, 23)), w.stream().map(Window::day).toList());
    }

    @Test
    void fullyCovered() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 1), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T03:00:00Z", "2026-11-20T17:00:00Z")));
        assertEquals(State.COVERED, r.state());
        assertTrue(r.gaps().isEmpty());
    }

    @Test
    void noShiftsMeansUncovered() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 2), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of());
        assertEquals(State.UNCOVERED, r.state());
        assertEquals(1, r.gaps().size());
        assertEquals(0, r.gaps().get(0).have());
        assertEquals(2, r.gaps().get(0).need());
    }

    @Test
    void lateStartLeavesAGapAtTheFront() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 1), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T08:00:00Z", "2026-11-20T20:00:00Z")));
        assertEquals(State.SHORT, r.state());
        assertEquals(t("2026-11-20T04:00:00Z"), r.gaps().get(0).start());
        assertEquals(t("2026-11-20T08:00:00Z"), r.gaps().get(0).end());
    }

    @Test
    void handoverGapBetweenTwoShiftsIsFound() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 1), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T04:00:00Z", "2026-11-20T10:00:00Z"), cover("2026-11-20T11:00:00Z", "2026-11-20T16:00:00Z")));
        assertEquals(State.SHORT, r.state());
        assertEquals(1, r.gaps().size());
        assertEquals(t("2026-11-20T10:00:00Z"), r.gaps().get(0).start());
        assertEquals(t("2026-11-20T11:00:00Z"), r.gaps().get(0).end());
    }

    @Test
    void backToBackShiftsHaveNoGap() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 1), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T04:00:00Z", "2026-11-20T10:00:00Z"), cover("2026-11-20T10:00:00Z", "2026-11-20T16:00:00Z")));
        assertEquals(State.COVERED, r.state());
    }

    @Test
    void twoGuardsNeededButOneOnTheSecondHalf() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 2), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T04:00:00Z", "2026-11-20T16:00:00Z"), cover("2026-11-20T04:00:00Z", "2026-11-20T10:00:00Z")));
        assertEquals(State.SHORT, r.state());
        assertEquals(1, r.gaps().size());
        assertEquals(1, r.gaps().get(0).have());
        assertEquals(t("2026-11-20T10:00:00Z"), r.gaps().get(0).start());
        assertEquals(t("2026-11-20T16:00:00Z"), r.gaps().get(0).end());
    }

    @Test
    void oneShortStretchAcrossAHandoverIsOneGap() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 2), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T04:00:00Z", "2026-11-20T10:00:00Z"), cover("2026-11-20T10:00:00Z", "2026-11-20T16:00:00Z")));
        assertEquals(State.SHORT, r.state());
        assertEquals(1, r.gaps().size());
        assertEquals(t("2026-11-20T04:00:00Z"), r.gaps().get(0).start());
        assertEquals(t("2026-11-20T16:00:00Z"), r.gaps().get(0).end());
        assertEquals(1, r.gaps().get(0).have());
    }

    @Test
    void exactlyEnoughGuardsIsNotAGap() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 2), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-20T04:00:00Z", "2026-11-20T16:00:00Z"), cover("2026-11-20T04:00:00Z", "2026-11-20T16:00:00Z")));
        assertEquals(State.COVERED, r.state());
    }

    @Test
    void shiftsOutsideTheWindowAreIgnored() {
        Window w = CoverageRules.windows(post(Set.of(5), "06:00", "18:00", 1), FRI, 1).get(0);
        Result r = CoverageRules.evaluate(w, List.of(cover("2026-11-19T04:00:00Z", "2026-11-19T16:00:00Z")));
        assertEquals(State.UNCOVERED, r.state());
    }

    @Test
    void evaluateAllIsOrderedByTime() {
        Post a = post(Set.of(5), "18:00", "06:00", 1);
        Post b = post(Set.of(5), "06:00", "18:00", 1);
        List<Result> out = CoverageRules.evaluateAll(List.of(a, b), FRI, 1, List.of());
        assertEquals(b.start(), out.get(0).window().post().start());
        assertEquals(2, out.size());
    }

    @Test
    void daysAreClamped() {
        assertEquals(7, CoverageRules.clampDays(null));
        assertEquals(1, CoverageRules.clampDays(0));
        assertEquals(14, CoverageRules.clampDays(99));
        assertEquals(3, CoverageRules.clampDays(3));
    }

    @Test
    void postValidation() {
        LocalTime s = LocalTime.of(6, 0), e = LocalTime.of(18, 0);
        assertEquals(Set.of(1, 5), CoverageRules.validate(" Gate ", Set.of(5, 1), s, e, 2));
        assertEquals("POST_NAME_INVALID", code(() -> CoverageRules.validate("  ", Set.of(1), s, e, 1)));
        assertEquals("POST_NAME_INVALID", code(() -> CoverageRules.validate("x".repeat(81), Set.of(1), s, e, 1)));
        assertEquals("POST_DAYS_INVALID", code(() -> CoverageRules.validate("G", Set.of(), s, e, 1)));
        assertEquals("POST_DAYS_INVALID", code(() -> CoverageRules.validate("G", Set.of(8), s, e, 1)));
        assertEquals("POST_DAYS_INVALID", code(() -> CoverageRules.validate("G", Set.of(0), s, e, 1)));
        assertEquals("POST_TIME_INVALID", code(() -> CoverageRules.validate("G", Set.of(1), null, e, 1)));
        assertEquals("POST_GUARDS_INVALID", code(() -> CoverageRules.validate("G", Set.of(1), s, e, 0)));
        assertEquals("POST_GUARDS_INVALID", code(() -> CoverageRules.validate("G", Set.of(1), s, e, 51)));
        assertEquals("POST_GUARDS_INVALID", code(() -> CoverageRules.validate("G", Set.of(1), s, e, null)));
    }

    // ---- source scans ----

    private static String svc() throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/CoverageService.java"));
    }

    @Test
    void onlyRealCoverCounts() throws Exception {
        String s = svc();
        assertTrue(s.contains("status IN ('SCHEDULED','ACTIVE','COMPLETED')"));
        for (String bad : new String[]{"MISSED", "CANCELLED", "PULLED"}) assertFalse(s.substring(s.indexOf("status IN"), s.indexOf("status IN") + 60).contains(bad));
        assertTrue(s.contains("deleted_at IS NULL"));
    }

    @Test
    void everyQueryIsTenantScoped() throws Exception {
        String s = svc();
        assertTrue(s.contains("p.tenant_id = ?"));
        assertTrue(s.contains("WHERE tenant_id = ? AND deleted_at IS NULL AND status IN"));
        assertTrue(s.contains("WHERE tenant_id = ? AND site_id = ? AND active"));
        assertTrue(s.contains("WHERE id = ? AND tenant_id = ? AND site_id = ? AND active"));
        assertTrue(s.contains("s.tenant_id = p.tenant_id"));
    }

    @Test
    void changingPostsNeedsManage() throws Exception {
        String c = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/api/CoverageController.java"));
        for (String m : new String[]{"@PostMapping(\"/api/v1/security/sites/{siteId}/coverage-posts\")", "@DeleteMapping"}) {
            int i = c.indexOf(m);
            assertTrue(i > 0);
            assertTrue(c.substring(i, i + 200).contains("SECURITY_MANAGE"), m);
        }
        int g = c.indexOf("@GetMapping(\"/api/v1/security/coverage\")");
        assertTrue(c.substring(g, g + 120).contains("SECURITY_READ"));
    }

    @Test
    void postsAreNeverHardDeleted() throws Exception {
        assertFalse(svc().toUpperCase().contains("DELETE FROM SECURITY_SITE_POSTS"));
    }

    /** The post-orders controller already owns /sites/{siteId}/posts; a second owner is an "ambiguous mapping" at startup. */
    @Test
    void coveragePostsDoNotShareTheUrlOfPostOrders() throws Exception {
        String c = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/api/CoverageController.java"));
        assertFalse(c.contains("/sites/{siteId}/posts"));
        assertEquals(3, c.split("/sites/\\{siteId\\}/coverage-posts", -1).length - 1);
    }

    @Test
    void theServiceHasABeanNameOfItsOwn() throws Exception {
        String s = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/CoverageService.java"));
        assertTrue(s.contains("@Service(\"securityCoverageService\")"));
    }
}
