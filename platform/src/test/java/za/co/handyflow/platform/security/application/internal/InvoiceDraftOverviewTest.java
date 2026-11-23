package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceDraftOverviewTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID S1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID S2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    /** Records the statement; hands the row mapper `rows` fake result rows, so the mapping runs too. */
    static class Rec extends JdbcTemplate {
        String sql; Object[] args; int rows;
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            sql = s; args = a;
            List<X> out = new ArrayList<>();
            try { for (int i = 0; i < rows; i++) out.add(m.mapRow(row(i), i)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
    }

    private static java.sql.ResultSet row(int i) {
        UUID id = new UUID(0, i + 1);
        return (java.sql.ResultSet) Proxy.newProxyInstance(InvoiceDraftOverviewTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            String label = a != null && a.length > 0 ? String.valueOf(a[0]) : "";
            return switch (m.getName()) {
                case "getObject" -> switch (label) {
                    case "id" -> id; case "site_id" -> S1;
                    case "created_at" -> OffsetDateTime.parse("2026-11-01T08:00:00Z");
                    default -> null;
                };
                case "getString" -> switch (label) {
                    case "site_name" -> "North Gate"; case "month" -> "2026-10"; case "basis" -> "SCHEDULED"; case "currency" -> "ZAR "; case "status" -> "DRAFT";
                    default -> null;
                };
                case "getLong" -> 123456L;
                case "getDouble" -> 1.5;
                case "getBoolean" -> label.equals("mixed_vat");
                default -> null;
            };
        });
    }

    private static InvoiceDraftOverviewService svc(Rec r) throws Exception {
        var k = InvoiceDraftOverviewService.class.getDeclaredConstructors()[0];
        Object[] ctorArgs = new Object[k.getParameterCount()];
        for (int i = 0; i < ctorArgs.length; i++) ctorArgs[i] = k.getParameterTypes()[i].isInstance(r) ? r : null; // JdbcTemplate gets the double, other collaborators stay null
        Object o = k.newInstance(ctorArgs);
        Field f = InvoiceDraftOverviewService.class.getDeclaredField("jdbc"); f.setAccessible(true); f.set(o, r);
        return (InvoiceDraftOverviewService) o;
    }

    private static Rec call(String month, String status, Set<UUID> allowed, int rows) throws Exception {
        Rec r = new Rec(); r.rows = rows; svc(r).list(TenantId.of(T), month, status, allowed); return r;
    }
    private static int marks(String s) { int n = 0; for (char c : s.toCharArray()) if (c == '?') n++; return n; }
    private static Set<UUID> two() { return new LinkedHashSet<>(List.of(S1, S2)); }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    @Test void noFiltersMeansEverythingInTheCompanyAndNothingElse() throws Exception {
        Rec r = call(null, null, null, 0);
        assertEquals(List.of(T), Arrays.asList(r.args));
        assertFalse(r.sql.contains("d.month = ?") || r.sql.contains("d.status = ?") || r.sql.contains(" IN (") || r.sql.contains("1 = 0"));
    }

    @Test void blankFiltersAreTheSameAsNone() throws Exception {
        assertEquals(List.of(T), Arrays.asList(call("  ", "", null, 0).args));
    }

    @Test void monthAndStatusAreBoundInTheOrderTheyAppear() throws Exception {
        Rec r = call("2026-10", "APPROVED", two(), 0);
        assertEquals(List.of(T, "2026-10", "APPROVED", S1, S2), Arrays.asList(r.args));
        assertEquals(marks(r.sql), r.args.length);
        assertTrue(r.sql.indexOf("d.month = ?") < r.sql.indexOf("d.status = ?") && r.sql.indexOf("d.status = ?") < r.sql.indexOf("d.site_id IN (?, ?)"));
    }

    @Test void aLimitedUserSeesOnlyTheirSitesAndAnEmptyScopeSeesNothing() throws Exception {
        Rec r = call(null, null, two(), 0);
        assertTrue(r.sql.contains(" AND d.site_id IN (?, ?)"));
        assertEquals(List.of(T, S1, S2), Arrays.asList(r.args));
        Rec e = call(null, null, new LinkedHashSet<>(), 0);
        assertTrue(e.sql.contains(" AND 1 = 0"));
        assertEquals(List.of(T), Arrays.asList(e.args));
    }

    @Test void aBadMonthOrStatusIsRefusedBeforeAnyQuery() throws Exception {
        for (String[] bad : new String[][]{{"2026-13", null}, {"October", null}, {"2026-1", null}, {null, "PAID"}, {null, "draft"}}) {
            Rec r = new Rec();
            HandyFlowException ex = assertThrows(HandyFlowException.class, () -> svc(r).list(TenantId.of(T), bad[0], bad[1], null));
            assertTrue(ex.getErrorCode().equals("DRAFT_MONTH") || ex.getErrorCode().equals("DRAFT_STATUS"), ex.getErrorCode());
            assertNull(r.sql, "no query may run");
        }
    }

    @Test void rowsAreMappedWithTheSiteNameAndATrimmedCurrency() throws Exception {
        Rec r = new Rec(); r.rows = 2;
        var out = svc(r).list(TenantId.of(T), null, null, null);
        assertEquals(2, out.items().size());
        var i = out.items().get(0);
        assertEquals("North Gate", i.siteName());
        assertEquals("ZAR", i.currency());
        assertEquals(123456L, i.totalCents());
        assertEquals(S1, i.siteId());
        assertEquals(1.5, i.unpricedHours());
        assertEquals("2026-10", i.month());
        assertFalse(out.truncated());
    }

    @Test void moreThanTheLimitIsCutAndSaysSo() throws Exception {
        Rec over = new Rec(); over.rows = InvoiceDraftOverviewService.LIMIT + 1;
        var a = svc(over).list(TenantId.of(T), null, null, null);
        assertTrue(a.truncated());
        assertEquals(InvoiceDraftOverviewService.LIMIT, a.items().size());
        Rec exact = new Rec(); exact.rows = InvoiceDraftOverviewService.LIMIT;
        var b = svc(exact).list(TenantId.of(T), null, null, null);
        assertFalse(b.truncated());
        assertEquals(InvoiceDraftOverviewService.LIMIT, b.items().size());
        assertTrue(over.sql.contains("LIMIT " + (InvoiceDraftOverviewService.LIMIT + 1)));
    }

    @Test void theQueryIsTenantScopedJoinsTheSiteOnTheTenantTooAndLeavesOutTheSnapshot() throws Exception {
        String s = read("application/internal/InvoiceDraftOverviewService.java");
        assertTrue(s.contains("WHERE d.tenant_id = ?"));
        assertTrue(s.contains("s.tenant_id = d.tenant_id"));
        assertTrue(s.contains("ORDER BY d.month DESC, s.name, d.created_at DESC"));
        assertFalse(s.contains("d.snapshot"), "the list never carries the frozen snapshot");
        assertTrue(s.contains("SiteScopeRules.sqlFilter(\"d.site_id\", allowed)"));
        assertTrue(s.contains("SiteScopeRules.sqlArgs(allowed)"));
    }

    @Test void theEndpointIsAdminOnlyFeatureGuardedAndFilteredByTheUsersSites() throws Exception {
        String c = read("api/InvoiceDraftController.java");
        int at = c.indexOf("listAll(");
        String head = c.substring(c.lastIndexOf("@GetMapping", at), at);
        assertTrue(head.contains("\"/api/v1/security/invoice-drafts\""));
        assertTrue(head.contains("hasAuthority('SECURITY_ADMIN')"));
        String body = c.substring(at, c.indexOf("@GetMapping", at));
        assertTrue(body.contains("featureGuard.requireModule(\"security\")"));
        assertTrue(body.contains("overview.list(TenantContext.getTenantIdAsObject(), month, status, allowed())"));
        assertTrue(c.contains("SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"));
    }
}
