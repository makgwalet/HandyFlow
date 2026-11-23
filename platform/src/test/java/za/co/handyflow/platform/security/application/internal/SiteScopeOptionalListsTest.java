package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lists whose site is optional (patrol rounds, live guards, gate dashboard, checkpoints): a user limited to some sites now gets
 * all their sites instead of being asked to name one, and a site they chose must still be one of theirs.
 */
class SiteScopeOptionalListsTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID S1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID S2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static final String[] CONTROLLERS = {"PatrolController", "GateDashboardController", "LiveOperationsController", "CheckpointAdminController"};

    static class Rec extends JdbcTemplate {
        final List<String> sql = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();
        private void rec(String s, Object[] a) { sql.add(s); args.add(a); }
        @Override public Map<String, Object> queryForMap(String s, Object... a) { rec(s, a); return new HashMap<>(); }
        @Override public <X> X queryForObject(String s, Class<X> t, Object... a) { rec(s, a); return null; }
        @Override public List<Map<String, Object>> queryForList(String s, Object... a) { rec(s, a); return new ArrayList<>(); }
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) { rec(s, a); return new ArrayList<>(); }
    }

    private static Set<UUID> two() { return new LinkedHashSet<>(List.of(S1, S2)); }
    private static int marks(String s) { int n = 0; for (char ch : s.toCharArray()) if (ch == '?') n++; return n; }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    private static Object service(Class<?> c, Rec rec) throws Exception {
        Constructor<?> k = c.getDeclaredConstructors()[0];
        Object[] ctorArgs = new Object[k.getParameterCount()];
        for (int i = 0; i < ctorArgs.length; i++) ctorArgs[i] = k.getParameterTypes()[i].isInstance(rec) ? rec : null; // JdbcTemplate gets the double, other collaborators stay null
        Object svc = k.newInstance(ctorArgs);
        Field f = c.getDeclaredField("jdbc"); f.setAccessible(true); f.set(svc, rec);
        return svc;
    }

    /** What each service is called with; `column` is the site column its filter must use. */
    private interface Call { void run(Rec rec, UUID siteId, Set<UUID> allowed) throws Exception; }
    private record Spec(String name, String column, Call call) {}

    private static final TenantId TID = TenantId.of(T);
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private static List<Spec> specs() {
        return List.of(
            new Spec("gate", "e.site_id", (r, s, a) -> {
                Method m = GateDashboardService.class.getMethod("dashboard", TenantId.class, UUID.class, Set.class);
                m.invoke(service(GateDashboardService.class, r), TID, s, a); }),
            new Spec("live", "s.site_id", (r, s, a) -> {
                Method m = LiveOperationsService.class.getMethod("liveGuards", TenantId.class, UUID.class, Set.class);
                m.invoke(service(LiveOperationsService.class, r), TID, s, a); }),
            new Spec("patrol", "r.site_id", (r, s, a) -> {
                Method m = PatrolOverviewService.class.getMethod("list", TenantId.class, Instant.class, Instant.class, UUID.class, String.class, Set.class);
                m.invoke(service(PatrolOverviewService.class, r), TID, NOW.minusSeconds(86400), NOW, s, "MISSED", a); }),
            new Spec("checkpoints", "c.site_id", (r, s, a) -> {
                Method m = CheckpointAdminService.class.getMethod("list", TenantId.class, UUID.class, boolean.class, Set.class);
                m.invoke(service(CheckpointAdminService.class, r), TID, s, false, a); }));
    }

    private static Rec run(Spec sp, UUID siteId, Set<UUID> allowed) throws Exception { Rec r = new Rec(); sp.call().run(r, siteId, allowed); return r; }

    private static List<Integer> filtered(Spec sp, Rec r) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < r.sql.size(); i++) if (r.sql.get(i).contains(" AND " + sp.column()) || r.sql.get(i).contains(" AND 1 = 0")) out.add(i);
        return out;
    }

    // ---- the shared rule ----

    @Test void theClauseIsOneSiteWhenASiteWasChosen() {
        assertEquals(" AND x.site_id = ?", SiteScopeRules.siteClause("x.site_id", S1, two()));
        assertEquals(" AND x.site_id = ?", SiteScopeRules.siteClause("x.site_id", S1, null));
        assertEquals(List.of(S1), SiteScopeRules.siteArgs(S1, two()));
    }

    @Test void theClauseIsAllTheUsersSitesWhenNoneWasChosen() {
        assertEquals(" AND x.site_id IN (?, ?)", SiteScopeRules.siteClause("x.site_id", null, two()));
        assertEquals(List.of(S1, S2), SiteScopeRules.siteArgs(null, two()));
    }

    @Test void theWholeCompanyIsNotFilteredAndAnEmptyScopeSeesNothing() {
        assertEquals("", SiteScopeRules.siteClause("x.site_id", null, null));
        assertEquals(List.of(), SiteScopeRules.siteArgs(null, null));
        assertEquals(" AND 1 = 0", SiteScopeRules.siteClause("x.site_id", null, new LinkedHashSet<>()));
        assertEquals(List.of(), SiteScopeRules.siteArgs(null, new LinkedHashSet<>()));
    }

    // ---- the four services, against a recording JdbcTemplate ----

    @Test void everyStatementHasAsManyValuesAsPlaceholdersInEveryMode() throws Exception {
        for (Spec sp : specs()) {
            for (UUID site : new UUID[]{null, S1}) {
                for (Set<UUID> allowed : new Set[]{null, two(), new LinkedHashSet<UUID>()}) {
                    Rec r = run(sp, site, allowed);
                    assertFalse(r.sql.isEmpty(), sp.name());
                    for (int i = 0; i < r.sql.size(); i++) assertEquals(marks(r.sql.get(i)), r.args.get(i).length, sp.name() + " site=" + site + " allowed=" + allowed + " :: " + r.sql.get(i));
                }
            }
        }
    }

    @Test void aLimitedUserWhoNamesNoSiteSeesAllTheirSitesTogether() throws Exception {
        for (Spec sp : specs()) {
            Rec r = run(sp, null, two());
            List<Integer> f = filtered(sp, r);
            assertFalse(f.isEmpty(), sp.name());
            for (int i : f) {
                String s = r.sql.get(i);
                assertTrue(s.contains(" AND " + sp.column() + " IN (?, ?)"), sp.name() + " :: " + s);
                int at = marks(s.substring(0, s.indexOf(" AND " + sp.column() + " IN (")));
                assertEquals(S1, r.args.get(i)[at], sp.name() + " first site value must sit at its placeholder");
                assertEquals(S2, r.args.get(i)[at + 1], sp.name());
            }
        }
    }

    @Test void aSiteTheyChoseNarrowsTheListToThatSiteOnly() throws Exception {
        for (Spec sp : specs()) {
            Rec r = run(sp, S1, two());
            List<Integer> f = filtered(sp, r);
            assertFalse(f.isEmpty(), sp.name());
            for (int i : f) {
                String s = r.sql.get(i);
                assertTrue(s.contains(" AND " + sp.column() + " = ?"), sp.name() + " :: " + s);
                assertFalse(s.contains(" IN (?"), sp.name());
                int at = marks(s.substring(0, s.indexOf(" AND " + sp.column() + " = ?")));
                assertEquals(S1, r.args.get(i)[at], sp.name());
                assertFalse(Arrays.asList(r.args.get(i)).contains(S2), sp.name() + " must not see the other site");
            }
        }
    }

    @Test void theWholeCompanyIsNotFilteredAtAll() throws Exception {
        for (Spec sp : specs()) {
            Rec r = run(sp, null, null);
            for (int i = 0; i < r.sql.size(); i++) {
                assertFalse(r.sql.get(i).contains(sp.column() + " IN ("), sp.name());
                assertFalse(r.sql.get(i).contains(" AND " + sp.column() + " = ?"), sp.name());
                assertFalse(r.sql.get(i).contains("1 = 0"), sp.name());
            }
        }
    }

    @Test void theWholeCompanyCanStillNarrowToOneSite() throws Exception {
        for (Spec sp : specs()) {
            Rec r = run(sp, S2, null);
            List<Integer> f = filtered(sp, r);
            assertFalse(f.isEmpty(), sp.name());
            for (int i : f) assertTrue(Arrays.asList(r.args.get(i)).contains(S2), sp.name());
        }
    }

    @Test void aUserWithAnEmptyScopeGetsNothingRatherThanEverything() throws Exception {
        for (Spec sp : specs()) {
            Rec r = run(sp, null, new LinkedHashSet<>());
            List<Integer> f = filtered(sp, r);
            assertFalse(f.isEmpty(), sp.name());
            for (int i : f) {
                assertTrue(r.sql.get(i).contains(" AND 1 = 0"), sp.name());
                assertFalse(Arrays.asList(r.args.get(i)).contains(S1), sp.name());
            }
        }
    }

    @Test void everyStatementOfTheGateDashboardIsFiltered() throws Exception {
        Spec gate = specs().get(0);
        Rec r = run(gate, null, two());
        assertEquals(6, r.sql.size(), "the dashboard runs six statements");
        assertEquals(6, filtered(gate, r).size(), "each of them must carry the site filter, or a total leaks");
    }

    @Test void theOldSignaturesStillMeanTheWholeCompany() throws Exception {
        Rec r = new Rec();
        ((GateDashboardService) service(GateDashboardService.class, r)).dashboard(TID, null);
        ((LiveOperationsService) service(LiveOperationsService.class, r)).liveGuards(TID, null);
        ((PatrolOverviewService) service(PatrolOverviewService.class, r)).list(TID, NOW.minusSeconds(3600), NOW, null, null);
        ((CheckpointAdminService) service(CheckpointAdminService.class, r)).list(TID, null, true);
        assertFalse(r.sql.isEmpty());
        for (String s : r.sql) { assertFalse(s.contains(" IN (?"), s); assertFalse(s.contains("1 = 0"), s); }
    }

    // ---- the controllers ----

    @Test void theControllersCheckAChosenSiteAndThenPassTheUsersSitesToTheService() throws Exception {
        for (String c : CONTROLLERS) {
            String s = read("api/" + c + ".java");
            assertTrue(s.contains("        if (siteId != null) site(siteId);\n"), c);
            assertFalse(s.contains("choice("), c + " no longer asks a limited user to name a site");
            assertTrue(s.contains("private java.util.Set<UUID> allowed() {"), c);
            assertTrue(s.contains("SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"), c);
            assertTrue(s.contains("allowed())"), c + " must hand the user's sites to the service");
            assertTrue(s.indexOf("if (siteId != null) site(siteId);") < s.indexOf("allowed()))"), c + ": the site check comes before the work");
        }
    }

    @Test void theServicesBuildTheirFilterWithTheSharedRule() throws Exception {
        for (String sv : new String[]{"GateDashboardService", "LiveOperationsService", "PatrolOverviewService", "CheckpointAdminService"}) {
            String s = read("application/internal/" + sv + ".java");
            assertTrue(s.contains("SiteScopeRules.siteClause("), sv);
            assertTrue(s.contains("SiteScopeRules.siteArgs("), sv);
            assertFalse(s.contains("sql.append(\" AND r.site_id = ?\")") || s.contains("sql.append(\" AND s.site_id = ?\")") || s.contains("sql.append(\" AND c.site_id = ?\")"), sv + " still has a single-site filter");
        }
        String g = read("application/internal/GateDashboardService.java");
        assertFalse(g.contains("if (siteId != null) p.add(siteId);"), "the by-site and by-gate parameter lists must use the shared site values");
    }
}
