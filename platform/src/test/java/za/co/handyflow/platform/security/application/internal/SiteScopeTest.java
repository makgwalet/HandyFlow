package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static String src(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    // ── rules ────────────────────────────────────────────────────────────────

    @Test
    void noScopeMeansTheWholeCompany() {
        assertFalse(SiteScopeRules.restricted(null));
        assertTrue(SiteScopeRules.allows(null, A));
        assertDoesNotThrow(() -> SiteScopeRules.requireSite(null, A));
        assertDoesNotThrow(() -> SiteScopeRules.requireUnrestricted(null));
    }

    @Test
    void aScopeLimitsTheUserToItsSites() {
        Set<UUID> allowed = Set.of(A, B);
        assertTrue(SiteScopeRules.restricted(allowed));
        assertTrue(SiteScopeRules.allows(allowed, A));
        assertFalse(SiteScopeRules.allows(allowed, C));
        assertEquals("SITE_NOT_FOUND", code(() -> SiteScopeRules.requireSite(allowed, C)));
        assertEquals("SITE_SCOPE_LIMITED", code(() -> SiteScopeRules.requireUnrestricted(allowed)));
    }

    @Test
    void aScopeWithNoSitesSeesNothing() {
        Set<UUID> none = new HashSet<>();
        assertTrue(SiteScopeRules.restricted(none));
        assertFalse(SiteScopeRules.allows(none, A));
    }

    @Test
    void aSiteOutsideTheScopeLooksExactlyLikeOneThatDoesNotExist() {
        HandyFlowException out = assertThrows(HandyFlowException.class, () -> SiteScopeRules.requireSite(Set.of(A), C));
        HandyFlowException missing = SiteScopeRules.notFound();
        assertEquals(missing.getErrorCode(), out.getErrorCode());
        assertEquals(missing.getMessage(), out.getMessage());
        assertEquals(missing.getStatus(), out.getStatus());
    }

    @Test
    void aMissingSiteIsNeverAllowed() {
        assertFalse(SiteScopeRules.allows(null, null));
        assertFalse(SiteScopeRules.allows(Set.of(A), null));
    }

    @Test
    void nobodyChangesTheirOwnScope() {
        assertEquals("SCOPE_SELF", code(() -> SiteScopeRules.requireNotSelf(A, A)));
        assertEquals("USER_REQUIRED", code(() -> SiteScopeRules.requireNotSelf(A, null)));
        assertDoesNotThrow(() -> SiteScopeRules.requireNotSelf(A, B));
    }

    @Test
    void listsAreCleanedAndBounded() {
        assertEquals(List.of(), SiteScopeRules.clean(null, "sites"));
        assertEquals(List.of(A, B), SiteScopeRules.clean(Arrays.asList(A, null, B, A), "sites"));
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i <= SiteScopeRules.MAX_ITEMS; i++) many.add(UUID.randomUUID());
        assertEquals("SCOPE_TOO_MANY", code(() -> SiteScopeRules.clean(many, "sites")));
        assertEquals(SiteScopeRules.MAX_ITEMS, SiteScopeRules.clean(many.subList(0, SiteScopeRules.MAX_ITEMS), "sites").size());
    }

    @Test
    void keepFiltersItemsBySite() {
        record Row(UUID site, String n) {}
        List<Row> rows = List.of(new Row(A, "a"), new Row(B, "b"), new Row(C, "c"), new Row(null, "x"));
        assertEquals(4, SiteScopeRules.keep(null, rows, Row::site).size());
        assertEquals(List.of("a", "c"), SiteScopeRules.keep(Set.of(A, C), rows, Row::site).stream().map(Row::n).toList());
        assertEquals(List.of(), SiteScopeRules.keep(Set.of(), rows, Row::site));
    }

    // ── what the code does ──────────────────────────────────────────────────

    @Test
    void everySiteEndpointChecksTheScope() throws Exception {
        for (String f : new String[]{"OccurrenceBookController", "MonthlyPackController", "MonthlyPackEmailController", "CoverageController"}) {
            String c = src("api/" + f + ".java");
            int paths = count(c, "@PathVariable UUID siteId");
            int checks = count(c, "site(siteId);");
            assertTrue(paths > 0, f);
            assertEquals(paths, checks, f + ": every path with a siteId must check the scope");
            assertTrue(c.contains("required = false"), f);
        }
        String s = src("api/SiteController.java");
        int paths = count(s, "@PathVariable UUID siteId") + count(s, "@PathVariable UUID id");
        assertEquals(paths, count(s, "site(siteId);") + count(s, "site(id);"));
        assertEquals(12, paths);
    }

    @Test
    void theCheckComesRightAfterTheModuleCheckAndBeforeAnyWork() throws Exception {
        for (String f : new String[]{"OccurrenceBookController", "MonthlyPackController", "MonthlyPackEmailController", "CoverageController", "SiteController"}) {
            String c = src("api/" + f + ".java");
            Matcher m = Pattern.compile("requireModule\\(\"security\"\\);\\n\\s+site\\((siteId|id)\\);").matcher(c);
            int n = 0;
            while (m.find()) n++;
            int all = count(c, "site(siteId);") + count(c, "site(id);");
            assertEquals(all, n, f);
        }
    }

    @Test
    void guardEndpointsAreNotScopedByStaffScope() throws Exception {
        String c = src("api/OccurrenceBookController.java");
        int g = c.indexOf("/api/v1/guard/ob/entries");
        String body = c.substring(g, c.indexOf("}\n", c.indexOf("guardEntry", g)));
        assertFalse(body.contains("site("), "the guard's site comes from the open session");
        assertTrue(body.contains("resolveCurrentSiteForGuard"));
    }

    @Test
    void listCreateAndCompanyWideSettingsAreLimited() throws Exception {
        String s = src("api/SiteController.java");
        assertTrue(s.contains("siteService.getSitesByBranch(TenantContext.getTenantIdAsObject(), branchId, pageable, allowed)"));
        assertTrue(s.contains("siteService.getSites(TenantContext.getTenantIdAsObject(), pageable, allowed)"));
        int create = s.indexOf("createSite(");
        assertTrue(s.substring(create, s.indexOf("siteService.createSite(", create)).contains("checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"));
        String m = src("api/MonthlyPackEmailController.java");
        int set = m.indexOf("setContact(");
        assertTrue(m.substring(set, m.indexOf("service.setContact(", set)).contains("checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"));
        String cov = src("api/CoverageController.java");
        int board = cov.indexOf("board(");
        String b = cov.substring(board, cov.indexOf("coverage.board(", board));
        assertTrue(b.contains("allowedSites(scope"));
        assertTrue(b.contains("if (siteId != null)") && b.contains("requireSite(allowed, siteId)"));
        assertTrue(cov.contains("coverage.board(TenantContext.getTenantIdAsObject(), siteId, days, allowed)"));
    }

    @Test
    void theBoardFiltersPostsInSqlAndAnEmptyScopeSeesNone() throws Exception {
        String c = src("application/internal/CoverageService.java");
        assertTrue(c.contains("if (allowedSites.isEmpty()) return new Board(from, days, 0, 0, 0, List.of());"));
        assertTrue(c.contains("p.site_id IN ("));
        assertTrue(c.contains("args.addAll(allowedSites)"));
        assertTrue(c.contains("return board(tenantId, siteId, daysAsked, Instant.now(), null);"), "the old signature still means the whole company");
    }

    @Test
    void theScopeQueriesAreTenantScopedAndOnlyCountLiveSites() throws Exception {
        String s = src("application/internal/SiteScopeService.java");
        int a = s.indexOf("ALLOWED_SITES = ");
        String q = s.substring(a, s.indexOf("\"\"\";", a));
        assertTrue(q.contains("s.tenant_id = ?") && q.contains("s.deleted_at IS NULL"));
        assertEquals(5, count(q, "tenant_id = ?"), "the site query and each of its four sub-selects are tenant scoped");
        assertEquals(9, count(q, "?"), "nine bind values, matching allowedSites()");
        assertTrue(q.contains("scope_type = 'SITE'") && q.contains("scope_type = 'CLIENT'"));
        assertTrue(q.contains("role IN ('MANAGER', 'VIEWER')"));
        assertTrue(s.contains("if (userId == null) return null;"));
    }

    @Test
    void changingScopeValidatesBeforeItWritesAndIsAudited() throws Exception {
        String s = src("application/internal/SiteScopeService.java");
        int r = s.indexOf("public View replace");
        String body = s.substring(r, s.indexOf("private void insert"));
        int del = body.indexOf("DELETE FROM security_user_scopes");
        for (String step : new String[]{"requireNotSelf(actor, userId)", "SiteScopeRules.clean(clientIds", "SiteScopeRules.clean(siteIds", "FROM users WHERE id = ? AND tenant_id = ?", "FROM security_sites WHERE id = ? AND tenant_id = ?", "FROM customers WHERE id = ? AND tenant_id = ?"}) {
            assertTrue(body.contains(step), step);
            assertTrue(body.indexOf(step) < del, step + " must come before the delete");
        }
        assertTrue(body.contains("SITE_SCOPE_SET"));
        assertTrue(body.indexOf("INSERT INTO security_user_scopes") < 0 || body.indexOf("SITE_SCOPE_SET") > 0);
        assertTrue(s.contains("@Transactional\n    public View replace"));
    }

    @Test
    void adminEndpointsNeedAdminAndTheOwnViewNeedsOnlyRead() throws Exception {
        String c = src("api/SiteScopeController.java");
        assertEquals(3, count(c, "featureGuard.requireModule(\"security\")"));
        int mine = c.indexOf("/my-site-scope");
        assertTrue(c.substring(mine, c.indexOf("public", mine)).contains("SECURITY_READ"));
        int get = c.indexOf("@GetMapping(\"/api/v1/security/users");
        assertTrue(c.substring(get, c.indexOf("public", get)).contains("SECURITY_ADMIN"));
        int put = c.indexOf("@PutMapping");
        assertTrue(c.substring(put, c.indexOf("public", put)).contains("SECURITY_ADMIN"));
        assertTrue(c.contains("TenantContext.getCurrentUserId(), userId, r.clientIds(), r.siteIds()"));
        assertFalse(c.contains("tenantId\""), "the company never comes from the request");
    }

    @Test
    void theMigrationIsTenantScopedAndCleansUpWithTheUser() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V862__security_user_scopes.sql"));
        assertTrue(m.contains("REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(m.contains("REFERENCES tenants(id)"));
        assertTrue(m.contains("CHECK (scope_type IN ('CLIENT', 'SITE'))"));
        assertTrue(m.contains("UNIQUE (tenant_id, user_id, scope_type, scope_id)"));
    }

    private static int count(String s, String what) {
        int n = 0, i = 0;
        while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); }
        return n;
    }
}
