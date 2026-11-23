package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeDashboardTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID S1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID S2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    /** Records every statement and its parameters; answers with nothing. */
    static class Rec extends JdbcTemplate {
        final List<String> sql = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();
        private void rec(String s, Object[] a) { sql.add(s); args.add(a); }
        @Override public Map<String, Object> queryForMap(String s, Object... a) { rec(s, a); return new HashMap<>(); }
        @Override public <T> T queryForObject(String s, Class<T> t, Object... a) { rec(s, a); return null; }
        @Override public List<Map<String, Object>> queryForList(String s, Object... a) { rec(s, a); return new ArrayList<>(); }
        @Override public <T> List<T> query(String s, RowMapper<T> m, Object... a) { rec(s, a); return new ArrayList<>(); }
    }

    private static Dashboard run(Rec rec, Set<UUID> allowed) throws Exception {
        Constructor<?> c = SecurityDashboardService.class.getDeclaredConstructors()[0];
        Object[] ctorArgs = new Object[c.getParameterCount()];
        for (int i = 0; i < ctorArgs.length; i++) ctorArgs[i] = c.getParameterTypes()[i].isInstance(rec) ? rec : null; // JdbcTemplate gets the double, other collaborators stay null
        Object svc = c.newInstance(ctorArgs);
        Field f = SecurityDashboardService.class.getDeclaredField("jdbc"); f.setAccessible(true); f.set(svc, rec);
        Method m = SecurityDashboardService.class.getDeclaredMethod("dashboard", TenantId.class, Instant.class, Set.class);
        m.setAccessible(true);
        return (Dashboard) m.invoke(svc, TenantId.of(T), Instant.parse("2026-11-22T10:00:00Z"), allowed);
    }

    private static Set<UUID> two() { return new LinkedHashSet<>(List.of(S1, S2)); }
    private static int marks(String s) { int n = 0; for (char ch : s.toCharArray()) if (ch == '?') n++; return n; }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    // ---- behaviour against a recording JdbcTemplate ----

    @Test void everyStatementHasAsManyValuesAsPlaceholders() throws Exception {
        for (Set<UUID> allowed : new Set[]{null, two(), new LinkedHashSet<UUID>()}) {
            Rec r = new Rec(); run(r, allowed);
            for (int i = 0; i < r.sql.size(); i++) assertEquals(marks(r.sql.get(i)), r.args.get(i).length, "allowed=" + allowed + " :: " + r.sql.get(i));
        }
    }

    @Test void theWholeCompanyRunsAllTenStatementsUnfiltered() throws Exception {
        Rec r = new Rec(); Dashboard d = run(r, null);
        assertEquals(10, r.sql.size());
        assertNotNull(d.workforce()); assertNotNull(d.complaints());
        for (String s : r.sql) assertFalse(s.contains(" IN (?"), s);
    }

    @Test void aLimitedUserGetsOnlyTheSiteStatementsAndNoGuardFigures() throws Exception {
        Rec r = new Rec(); Dashboard d = run(r, two());
        assertEquals(7, r.sql.size());
        assertNull(d.workforce(), "guard figures are not about a site: absent, not zero");
        assertNull(d.complaints());
        for (String s : r.sql) {
            assertFalse(s.contains("FROM security_guards") || s.contains("security_guard_competencies") || s.contains("security_guard_complaints"), s);
            assertTrue(s.contains("tenant_id = ?"), s);
        }
    }

    @Test void everySiteStatementIsFilteredByTheSitesColumn() throws Exception {
        Rec r = new Rec(); run(r, two());
        String[] cols = {"site_id IN (?, ?)", "i.site_id IN (?, ?)", "s.site_id IN (?, ?)", "id IN (?, ?)"};
        int filtered = 0;
        for (String s : r.sql) { for (String c : cols) if (s.contains(c)) { filtered++; break; } }
        assertEquals(7, filtered);
        for (Object[] a : r.args) {
            List<Object> l = Arrays.asList(a);
            assertTrue(l.contains(S1) && l.contains(S2), Arrays.toString(a));
            assertTrue(l.indexOf(T) >= 0 && l.indexOf(T) < l.indexOf(S1), "the tenant comes before the sites: " + Arrays.toString(a));
        }
    }

    @Test void theListQueriesTakeTenantThenSitesThenTheRowLimit() throws Exception {
        Rec r = new Rec(); run(r, two());
        int lists = 0;
        for (int i = 0; i < r.sql.size(); i++) if (r.sql.get(i).contains("LIMIT ?")) {
            lists++;
            Object[] a = r.args.get(i);
            assertArrayEquals(new Object[]{T, S1, S2, 6}, a, r.sql.get(i));
        }
        assertEquals(2, lists);
    }

    @Test void aLimitedUserWithNoSitesMatchesNothingAndBindsNoSiteValues() throws Exception {
        Rec r = new Rec(); run(r, new LinkedHashSet<>());
        assertEquals(7, r.sql.size());
        for (int i = 0; i < r.sql.size(); i++) {
            assertTrue(r.sql.get(i).contains("AND 1 = 0"), r.sql.get(i));
            assertFalse(Arrays.asList(r.args.get(i)).contains(S1));
        }
    }

    // ---- the ranked list without guard or complaint figures ----

    @Test void theAttentionListLeavesOutWhatItHasNoFiguresFor() {
        Shifts sh = new Shifts(1, 2, 1, 1, 0); Incidents inc = new Incidents(3, 1, 1, 4); Gate g = new Gate(2, 1, 5);
        List<AttentionItem> limited = DashboardAttention.build(2, 1, sh, null, inc, null, g);
        Set<String> codes = new HashSet<>(); for (AttentionItem i : limited) codes.add(i.code());
        assertTrue(codes.containsAll(Set.of("CRITICAL_INCIDENTS", "ALARMS", "NOT_STARTED", "MISSED", "UNACKNOWLEDGED", "OVERSTAYED")), codes.toString());
        assertFalse(codes.contains("PSIRA_EXPIRED") || codes.contains("URGENT_COMPLAINTS") || codes.contains("PSIRA_EXPIRING") || codes.contains("COMPETENCIES_EXPIRED") || codes.contains("COMPETENCIES_EXPIRING"));
        List<AttentionItem> all = DashboardAttention.build(2, 1, sh, new Workforce(10, 9, 2, 1, 1, 1), inc, new Complaints(2, 1), g);
        Set<String> every = new HashSet<>(); for (AttentionItem i : all) every.add(i.code());
        assertTrue(every.containsAll(Set.of("PSIRA_EXPIRED", "URGENT_COMPLAINTS", "PSIRA_EXPIRING", "COMPETENCIES_EXPIRED", "COMPETENCIES_EXPIRING")));
    }

    // ---- controllers and service wiring ----

    private static String method(String src, String name) {
        int at = name.contains("(") ? src.indexOf(name) : -1;
        if (at < 0) {
            Matcher mm = Pattern.compile("(public|private|static)[^;=(]*\\s" + Pattern.quote(name) + "\\(").matcher(src);
            assertTrue(mm.find(), "missing " + name);
            at = mm.start();
        }
        int open = src.indexOf('{', at), d = 0, j = open;
        for (; j < src.length(); j++) { char c = src.charAt(j); if (c == '{') d++; if (c == '}' && --d == 0) break; }
        return src.substring(at, j + 1);
    }

    @Test void theDashboardEndpointPassesTheUsersSites() throws Exception {
        String c = read("api/SecurityDashboardController.java");
        assertTrue(method(c, "dashboard").contains("service.dashboard(TenantContext.getTenantIdAsObject(), allowed())"));
        assertTrue(method(c, "allowed").contains("SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())"));
    }

    @Test void theOldDashboardSignatureStillMeansTheWholeCompany() throws Exception {
        String s = read("application/internal/SecurityDashboardService.java");
        assertTrue(s.contains("public Dashboard dashboard(TenantId tenantId) { return dashboard(tenantId, Instant.now(), null); }"));
        assertTrue(s.contains("Dashboard dashboard(TenantId tenantId, Instant now) { return dashboard(tenantId, now, null); }"));
    }

    @Test void siteCoverageIsCheckedAgainstTheSiteAndTheCompanyWideReportsNeedTheWholeCompany() throws Exception {
        String c = read("api/ReportingController.java");
        for (String n : new String[]{"getSiteCoverage", "getSiteCoveragePdf"}) {
            String m = method(c, n);
            assertTrue(m.contains("site(siteId);"), n);
            assertTrue(m.indexOf("site(siteId);") < m.indexOf("reportingService."), n + ": check before the report is built");
        }
        for (String n : new String[]{"getGuardAttendance", "getGuardAttendancePdf", "getMonthlySummaryPdf"}) {
            String m = method(c, n);
            assertTrue(m.contains("wholeCompany();"), n);
            assertTrue(m.indexOf("wholeCompany();") < m.indexOf("reportingService."), n + ": check before the report is built");
        }
        // The monthly summary (JSON) is narrowed to the user's sites instead (chunk 96); its PDF is labelled company-wide, so it stays whole-company.
        String summary = method(c, "getMonthlySummary");
        assertFalse(summary.contains("wholeCompany()"));
        assertTrue(summary.contains("reportingService.getMonthlySummaryReport(tenantId, parseMonth(month), allowed())"));
        assertEquals(6, c.split("@GetMapping", -1).length - 1, "a new report endpoint must be classified here");
    }

    @Test void theCatalogueHidesWhoGeneratedWhatFromALimitedUser() throws Exception {
        String ctl = read("api/ReportCatalogueController.java");
        assertTrue(method(ctl, "catalogue").contains("runService.catalogue(TenantContext.getTenantIdAsObject(), allowed() != null)"));
        String svc = read("application/internal/ReportRunService.java");
        String m = method(svc, "catalogue(TenantId tenantId, boolean limited)");
        assertTrue(m.contains("if (limited) return new Catalogue(cards(List.of()), List.of());"));
        assertTrue(m.indexOf("if (limited)") < m.indexOf("jdbc."), "no history is read for a limited user");
        assertTrue(svc.contains("public Catalogue catalogue(TenantId tenantId) { return catalogue(tenantId, false); }"));
    }
}
