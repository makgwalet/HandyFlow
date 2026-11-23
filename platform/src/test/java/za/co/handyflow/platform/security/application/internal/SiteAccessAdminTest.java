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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SiteAccessAdminTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333332");
    private static final UUID B1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID B2 = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    /** Records every statement in order and answers the few questions the service asks. */
    static class Rec extends JdbcTemplate {
        record Stmt(String kind, String sql, Object[] args) {}
        final List<Stmt> log = new ArrayList<>();
        boolean userExists = true, branchesExist = true;
        int rows = 0;

        @Override public <X> List<X> queryForList(String s, Class<X> t, Object... a) {
            log.add(new Stmt("list", s, a));
            List<X> out = new ArrayList<>();
            if (s.contains("FROM users") && userExists) out.add(t.cast(USER));
            return out;
        }
        @Override public <X> X queryForObject(String s, Class<X> t, Object... a) {
            log.add(new Stmt("one", s, a));
            if (s.contains("FROM security_branches")) return t.cast(branchesExist ? 1 : 0);
            return null;
        }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return 1; }
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            log.add(new Stmt("query", s, a));
            List<X> out = new ArrayList<>();
            try { for (int i = 0; i < rows; i++) out.add(m.mapRow(row(), i)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
        List<Stmt> of(String kind) { return log.stream().filter(x -> x.kind().equals(kind)).toList(); }
    }

    private static java.sql.ResultSet row() {
        return (java.sql.ResultSet) Proxy.newProxyInstance(SiteAccessAdminTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            String label = a != null && a.length > 0 ? String.valueOf(a[0]) : "";
            return switch (m.getName()) {
                case "getObject" -> USER;
                case "getString" -> switch (label) { case "first_name" -> "Thandi"; case "last_name" -> "Nkosi "; case "email" -> "t@x.co"; case "status" -> "ACTIVE"; default -> null; };
                case "getInt" -> switch (label) { case "regions" -> 4; case "branches" -> 2; case "clients" -> 1; case "sites" -> 3; case "visible_sites" -> 0; default -> 0; };
                default -> null;
            };
        });
    }

    private static SiteAccessAdminService svc(Rec r) throws Exception {
        // The services are built with their real constructors; the recording JDBC double is also set on the field so either shape works.
        var sk = SiteScopeService.class.getDeclaredConstructors()[0];
        SiteScopeService scope = (SiteScopeService) (sk.getParameterCount() == 0 ? sk.newInstance() : sk.newInstance(r));
        set(scope, "jdbc", r);
        var ak = SiteAccessAdminService.class.getDeclaredConstructors()[0];
        SiteAccessAdminService s = (SiteAccessAdminService) (ak.getParameterCount() == 0 ? ak.newInstance() : ak.newInstance(r, scope));
        set(s, "jdbc", r); set(s, "scope", scope);
        return s;
    }
    private static void set(Object o, String name, Object v) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, v); }

    private static SiteAccessAdminService.BranchEntry e(UUID b, String role) { return new SiteAccessAdminService.BranchEntry(b, role); }
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }
    private static int indexOf(Rec r, String frag) { for (int i = 0; i < r.log.size(); i++) if (r.log.get(i).sql().contains(frag)) return i; return -1; }
    private static int lastIndexOf(Rec r, String frag) { for (int i = r.log.size() - 1; i >= 0; i--) if (r.log.get(i).sql().contains(frag)) return i; return -1; }
    private static int marks(String s) { int n = 0; for (char c : s.toCharArray()) if (c == '?') n++; return n; }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    // ---- rules ----

    @Test void entriesAreCleanedAndTheRoleIsInCapitals() {
        List<BranchAccessRules.Entry> out = BranchAccessRules.clean(Arrays.asList(new BranchAccessRules.Entry(B1, " viewer "), null, new BranchAccessRules.Entry(B2, "Manager")));
        assertEquals(List.of(new BranchAccessRules.Entry(B1, "VIEWER"), new BranchAccessRules.Entry(B2, "MANAGER")), out);
        assertEquals(List.of(), BranchAccessRules.clean(null));
    }

    @Test void aBadEntryIsRefusedWithItsOwnCode() {
        assertEquals("BRANCH_ROLE", code(() -> BranchAccessRules.clean(List.of(new BranchAccessRules.Entry(B1, "GUARD")))));
        assertEquals("BRANCH_ROLE", code(() -> BranchAccessRules.clean(List.of(new BranchAccessRules.Entry(B1, null)))));
        assertEquals("BRANCH_ROLE", code(() -> BranchAccessRules.clean(List.of(new BranchAccessRules.Entry(B1, "")))));
        assertEquals("BRANCH_REQUIRED", code(() -> BranchAccessRules.clean(List.of(new BranchAccessRules.Entry(null, "VIEWER")))));
        assertEquals("BRANCH_DUPLICATE", code(() -> BranchAccessRules.clean(List.of(new BranchAccessRules.Entry(B1, "VIEWER"), new BranchAccessRules.Entry(B1, "MANAGER")))));
    }

    @Test void thereIsALimitOnHowManyBranches() {
        List<BranchAccessRules.Entry> ok = new ArrayList<>(), tooMany = new ArrayList<>();
        for (int i = 0; i < SiteScopeRules.MAX_ITEMS + 1; i++) { BranchAccessRules.Entry x = new BranchAccessRules.Entry(new UUID(1, i), "VIEWER"); tooMany.add(x); if (i < SiteScopeRules.MAX_ITEMS) ok.add(x); }
        assertEquals(SiteScopeRules.MAX_ITEMS, BranchAccessRules.clean(ok).size());
        assertEquals("BRANCH_TOO_MANY", code(() -> BranchAccessRules.clean(tooMany)));
    }

    @Test void theKeepFilterListsOnePlaceholderPerBranchKept() {
        assertEquals("", BranchAccessRules.keepFilter(0));
        assertEquals(" AND branch_id NOT IN (?)", BranchAccessRules.keepFilter(1));
        assertEquals(" AND branch_id NOT IN (?, ?, ?)", BranchAccessRules.keepFilter(3));
    }

    // ---- replaceBranches ----

    @Test void youCannotChangeYourOwnAccessAndNothingRuns() throws Exception {
        Rec r = new Rec();
        SiteAccessAdminService s = svc(r);
        assertEquals("SCOPE_SELF", code(() -> s.replaceBranches(TenantId.of(T), ACTOR, ACTOR, List.of(e(B1, "VIEWER")))));
        assertTrue(r.log.isEmpty());
    }

    @Test void aBadRoleOrDuplicateIsRefusedBeforeAnyStatement() throws Exception {
        for (List<SiteAccessAdminService.BranchEntry> bad : List.of(List.of(e(B1, "GUARD")), List.of(e(B1, "VIEWER"), e(B1, "VIEWER")), List.of(e(null, "VIEWER")))) {
            Rec r = new Rec();
            assertThrows(HandyFlowException.class, () -> svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, bad));
            assertTrue(r.log.isEmpty(), "nothing may run for " + bad);
        }
    }

    @Test void anUnknownUserOrBranchChangesNothing() throws Exception {
        Rec noUser = new Rec(); noUser.userExists = false;
        assertEquals("USER_NOT_FOUND", assertThrows(HandyFlowException.class, () -> svc(noUser).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "VIEWER")))).getErrorCode());
        assertTrue(noUser.of("update").isEmpty());
        Rec noBranch = new Rec(); noBranch.branchesExist = false;
        assertEquals("BRANCH_UNKNOWN", assertThrows(HandyFlowException.class, () -> svc(noBranch).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "VIEWER")))).getErrorCode());
        assertTrue(noBranch.of("update").isEmpty(), "no assignment may be touched when a branch is unknown");
    }

    @Test void theBranchesAreSwitchedOnAndTheOthersOffInOneOrderedSequence() throws Exception {
        Rec r = new Rec();
        svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "viewer"), e(B2, "MANAGER")));
        List<Rec.Stmt> ups = r.of("update");
        assertEquals(4, ups.size(), "one switch-off, two upserts, one audit row");
        // the user row is locked first, then the branches are checked, then anything is written
        int lock = indexOf(r, "FOR UPDATE"), firstWrite = r.log.indexOf(ups.get(0)), lastCheck = lastIndexOf(r, "FROM security_branches");
        assertTrue(lock >= 0 && lock < lastCheck && lastCheck < firstWrite, "lock, check branches, then write");
        Rec.Stmt off = ups.get(0);
        assertTrue(off.sql().contains("SET active = false") && off.sql().contains("entity_type = 'USER'") && off.sql().contains("role IN ('MANAGER', 'VIEWER')"));
        assertTrue(off.sql().contains("branch_id NOT IN (?, ?)"));
        assertEquals(List.of(T, USER, B1, B2), Arrays.asList(off.args()));
        Rec.Stmt a = ups.get(1), b = ups.get(2);
        for (Rec.Stmt u : List.of(a, b)) { assertTrue(u.sql().contains("ON CONFLICT (branch_id, entity_type, entity_id) DO UPDATE")); assertEquals(marks(u.sql()), u.args().length); }
        assertEquals(List.of(T, B1, USER, "VIEWER", ACTOR), Arrays.asList(a.args()));
        assertEquals(List.of(T, B2, USER, "MANAGER", ACTOR), Arrays.asList(b.args()));
        assertTrue(ups.get(3).sql().contains("'BRANCH_ACCESS_SET'"));
        assertTrue((String) ups.get(3).args()[3] != null && ((String) ups.get(3).args()[3]).contains(B2.toString()));
    }

    @Test void aBranchMustBeInTheCompanyAndSwitchedOn() throws Exception {
        Rec r = new Rec();
        svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "VIEWER")));
        Rec.Stmt check = r.of("one").stream().filter(x -> x.sql().contains("FROM security_branches")).findFirst().orElseThrow();
        assertTrue(check.sql().contains("id = ? AND tenant_id = ? AND active"), "a branch from another company or one switched off is not accepted");
        assertEquals(List.of(B1, T), Arrays.asList(check.args()));
    }

    @Test void theUpsertUpdatesTheRoleSwitchesItBackOnAndRecordsWho() throws Exception {
        Rec r = new Rec();
        svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "VIEWER")));
        String up = r.of("update").get(1).sql();
        assertTrue(up.contains("role = EXCLUDED.role"), "a changed role must be kept");
        assertTrue(up.contains("active = true"), "a switched-off branch given again must come back on");
        assertTrue(up.contains("assigned_by = EXCLUDED.assigned_by"));
    }

    @Test void anEmptyListSwitchesEverythingOffAndAddsNothing() throws Exception {
        Rec r = new Rec();
        svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, List.of());
        List<Rec.Stmt> ups = r.of("update");
        assertEquals(2, ups.size(), "the switch-off and the audit row");
        assertFalse(ups.get(0).sql().contains("NOT IN"));
        assertEquals(List.of(T, USER), Arrays.asList(ups.get(0).args()));
        assertTrue(r.of("one").isEmpty() || r.of("one").stream().noneMatch(x -> x.sql().contains("security_branches")));
    }

    @Test void nothingIsEverDeletedAndEveryStatementIsTenantScoped() throws Exception {
        Rec r = new Rec();
        svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "VIEWER")));
        for (Rec.Stmt s : r.log) {
            assertFalse(s.sql().contains("DELETE"), s.sql());
            assertEquals(marks(s.sql()), s.args().length, s.sql());
            assertTrue(s.sql().contains("tenant_id"), s.sql());
        }
    }

    @Test void theUpdateReturnsTheUsersViewAfterTheChange() throws Exception {
        Rec r = new Rec();
        svc(r).replaceBranches(TenantId.of(T), ACTOR, USER, List.of(e(B1, "VIEWER")));
        int lastUpdate = r.log.indexOf(r.of("update").get(r.of("update").size() - 1));
        assertTrue(r.log.size() > lastUpdate + 1, "the view is read after the writes");
        assertTrue(r.log.get(r.log.size() - 1).sql().contains("security_branch_assignments") || r.log.get(r.log.size() - 1).sql().contains("security_sites") || r.log.get(r.log.size() - 1).sql().contains("security_user_scopes"));
    }

    // ---- the overview ----

    @Test void theOverviewIsTenantScopedAndMapsEachRow() throws Exception {
        Rec r = new Rec(); r.rows = 2;
        List<SiteAccessAdminService.LimitedUser> out = svc(r).limitedUsers(TenantId.of(T));
        assertEquals(2, out.size());
        SiteAccessAdminService.LimitedUser u = out.get(0);
        assertEquals("Thandi Nkosi", u.name());
        assertEquals(2, u.branches()); assertEquals(1, u.clients()); assertEquals(3, u.sites()); assertEquals(0, u.visibleSites());
        assertEquals("t@x.co", u.email());
        assertEquals(4, u.regions());
        Rec.Stmt q = r.of("query").get(0);
        assertEquals(List.of(T), Arrays.asList(q.args()));
        assertEquals(marks(q.sql()), q.args().length, "every ? in the overview query is the tenant, and the tenant only once");
    }

    @Test void theOverviewListsOnlyLimitedUsersAndCountsLikeTheScopeRule() {
        String q = SiteAccessAdminService.LIMITED_USERS;
        assertTrue(q.contains("WHERE u.tenant_id = ?"));
        assertEquals(3, q.split("role IN \\('MANAGER', 'VIEWER'\\)", -1).length - 1, "branch counting, the visible sites and the filter all use the same roles");
        assertEquals(3, q.split("a\\.active AND a\\.role IN", -1).length - 1, "a switched-off branch never counts, in any of the three places");
        for (String part : new String[]{"scope_type = 'CLIENT'", "scope_type = 'SITE'", "s.customer_id IN", "s.branch_id IN", "s.deleted_at IS NULL", "a.active"}) assertTrue(q.contains(part), part);
        assertTrue(SiteScopeService.ALLOWED_SITES.contains("role IN ('MANAGER', 'VIEWER')"), "same roles as the rule that limits the user");
        assertEquals(3, q.split("security_user_regions g WHERE g\\.tenant_id = u\\.tenant_id AND g\\.user_id = u\\.id", -1).length - 1, "the count, the filter and the visible sites each look at the user's regions");
        assertTrue(q.contains("b.region_id IN"));
        assertTrue(q.contains("ORDER BY u.last_name, u.first_name, u.id"));
    }

    // ---- controller ----

    @Test void bothEndpointsAreAdminOnlyFeatureGuardedAndNeedTheWholeCompany() throws Exception {
        String c = read("api/SiteAccessAdminController.java");
        assertEquals(3, c.split("@(Get|Put)Mapping", -1).length - 1);
        assertEquals(3, c.split("hasAuthority\\('SECURITY_ADMIN'\\)", -1).length - 1);
        for (String name : new String[]{"limitedUsers", "setBranches", "setRegions"}) {
            int at = c.indexOf(" " + name + "(");
            String body = c.substring(at, c.indexOf("\n    }", at));
            assertTrue(body.contains("featureGuard.requireModule(\"security\");"), name);
            assertTrue(body.contains("wholeCompany();"), name);
            assertTrue(body.indexOf("featureGuard") < body.indexOf("wholeCompany();") && body.indexOf("wholeCompany();") < body.indexOf("service."), name);
        }
        assertTrue(c.contains("\"/api/v1/security/users/{userId}/branch-access\""));
        assertTrue(c.contains("\"/api/v1/security/site-access\""));
        assertTrue(c.contains("TenantContext.getCurrentUserId(), userId, r.branches()"), "the caller is the actor, the path user is the target");
        assertTrue(c.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private SiteScopeService scope;"));
        assertTrue(c.contains("SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"));
    }

    @Test void theServiceKeepsGuardAssignmentsAndHistory() throws Exception {
        String s = read("application/internal/SiteAccessAdminService.java");
        assertEquals(1, s.split("DELETE FROM", -1).length - 1, "the only delete is the user's own region rows, which are replaced as a set");
        assertTrue(s.contains("DELETE FROM security_user_regions WHERE tenant_id = ? AND user_id = ?"));
        assertTrue(s.contains("entity_type = 'USER'"));
        assertFalse(s.contains("entity_type = 'GUARD'"));
        assertTrue(s.contains("SiteScopeRules.requireNotSelf(actor, userId);"));
        assertTrue(s.indexOf("requireNotSelf") < s.indexOf("BranchAccessRules.clean") && s.indexOf("BranchAccessRules.clean") < s.indexOf("FOR UPDATE"));
    }
}
