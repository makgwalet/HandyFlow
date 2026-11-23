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

class SiteScopeRegionTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333332");
    private static final UUID R1 = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID R2 = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID S1 = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    static class Rec extends JdbcTemplate {
        record Stmt(String kind, String sql, Object[] args) {}
        final List<Stmt> log = new ArrayList<>();
        boolean userExists = true, regionsExist = true, scoped = true;
        List<UUID> sites = new ArrayList<>();
        @Override public <X> List<X> queryForList(String s, Class<X> t, Object... a) {
            log.add(new Stmt("list", s, a));
            List<X> out = new ArrayList<>();
            if (s.contains("FROM users") && userExists) out.add(t.cast(USER));
            if (s.startsWith("SELECT s.id FROM security_sites")) for (UUID u : sites) out.add(t.cast(u));
            return out;
        }
        @Override public <X> X queryForObject(String s, Class<X> t, Object... a) {
            log.add(new Stmt("one", s, a));
            if (s.contains("FROM security_regions")) return t.cast(regionsExist ? 1 : 0);
            if (s.startsWith("SELECT (EXISTS")) return t.cast(scoped);
            return null;
        }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return 1; }
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            log.add(new Stmt("query", s, a));
            List<X> out = new ArrayList<>();
            try { if (s.contains("FROM security_user_regions x JOIN security_regions")) { out.add(m.mapRow(row(), 0)); } } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
        List<Stmt> of(String kind) { return log.stream().filter(x -> x.kind().equals(kind)).toList(); }
        int at(String frag) { for (int i = 0; i < log.size(); i++) if (log.get(i).sql().contains(frag)) return i; return -1; }
    }

    private static java.sql.ResultSet row() {
        return (java.sql.ResultSet) Proxy.newProxyInstance(SiteScopeRegionTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) ->
                switch (m.getName()) { case "getObject" -> R1; case "getString" -> "KZN"; default -> null; });
    }

    private static void set(Object o, String name, Object v) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, v); }
    private static SiteScopeService scope(Rec r) throws Exception {
        var k = SiteScopeService.class.getDeclaredConstructors()[0];
        SiteScopeService s = (SiteScopeService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r));
        set(s, "jdbc", r); return s;
    }
    private static SiteAccessAdminService admin(Rec r) throws Exception {
        SiteScopeService sc = scope(r);
        var k = SiteAccessAdminService.class.getDeclaredConstructors()[0];
        SiteAccessAdminService s = (SiteAccessAdminService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r, sc));
        set(s, "jdbc", r); set(s, "scope", sc); return s;
    }
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }
    private static int marks(String s) { int n = 0; for (char c : s.toCharArray()) if (c == '?') n++; return n; }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static TenantId tid() { return TenantId.of(T); }

    // ---- who is limited, and to what ----

    @Test void aUserWhoOnlyHoldsARegionIsLimitedNotUnrestricted() throws Exception {
        Rec r = new Rec(); r.scoped = true; // the region has no branches yet: no site matches
        Set<UUID> allowed = scope(r).allowedSites(tid(), USER);
        assertNotNull(allowed, "limited, never the whole company");
        assertTrue(allowed.isEmpty(), "and sees nothing until the region has branches");
    }

    @Test void aUserWithNothingStaysTheWholeCompanyAndTheSiteQueryDoesNotRun() throws Exception {
        Rec r = new Rec(); r.scoped = false;
        assertNull(scope(r).allowedSites(tid(), USER));
        assertTrue(r.of("list").isEmpty());
    }

    @Test void theRegionMembersComeBackAsTheAllowedSites() throws Exception {
        Rec r = new Rec(); r.sites.add(S1);
        assertEquals(Set.of(S1), scope(r).allowedSites(tid(), USER));
    }

    @Test void everyPlaceholderInTheTwoScopeQueriesHasItsValue() throws Exception {
        Rec r = new Rec(); r.sites.add(S1);
        scope(r).allowedSites(tid(), USER);
        Rec.Stmt has = r.of("one").get(0), sites = r.of("list").get(0);
        assertEquals(marks(SiteScopeService.HAS_SCOPE), has.args().length);
        assertEquals(marks(SiteScopeService.ALLOWED_SITES), sites.args().length);
        assertEquals(6, has.args().length); assertEquals(9, sites.args().length);
        for (int i = 0; i < has.args().length; i++) assertEquals(i % 2 == 0 ? T : USER, has.args()[i], "has-scope arg " + i);
        assertEquals(T, sites.args()[0]);
        for (int i = 1; i < sites.args().length; i++) assertEquals(i % 2 == 1 ? T : USER, sites.args()[i], "site arg " + i);
    }

    @Test void theRegionPartOfTheQueriesIsLiveAndStaysInsideTheCompany() {
        String q = SiteScopeService.ALLOWED_SITES;
        assertTrue(q.contains("FROM security_branches b WHERE b.tenant_id = ? AND b.region_id IN"));
        assertTrue(q.contains("FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = ?"));
        assertTrue(SiteScopeService.HAS_SCOPE.contains("FROM security_user_regions WHERE tenant_id = ? AND user_id = ?"));
        assertFalse(q.substring(q.indexOf("FROM security_branches b")).contains("active"), "a region reaches its branches however the branch assignment table looks");
        assertEquals(2, SiteScopeService.HAS_SCOPE.split("OR EXISTS", -1).length - 1, "client/site part and region part, after the branch part");
    }

    @Test void viewShowsTheRegionsAndTheOldFiveArgumentShapeStillWorks() throws Exception {
        Rec r = new Rec(); r.sites.add(S1);
        SiteScopeService.View v = scope(r).view(tid(), USER);
        assertEquals(List.of(new SiteScopeService.Named(R1, "KZN")), v.regions());
        assertTrue(v.restricted());
        assertEquals(1, v.visibleSites());
        SiteScopeService.View old = new SiteScopeService.View(false, List.of(), List.of(), List.of(), -1);
        assertEquals(List.of(), old.regions());
        Rec.Stmt q = r.log.get(r.at("FROM security_user_regions x JOIN security_regions"));
        assertEquals(List.of(T, USER), Arrays.asList(q.args()));
        assertTrue(q.sql().contains("r.tenant_id = x.tenant_id"));
    }

    // ---- replaceRegions ----

    @Test void youCannotChangeYourOwnRegionsAndNothingRuns() throws Exception {
        Rec r = new Rec();
        assertEquals("SCOPE_SELF", code(() -> { try { admin(r).replaceRegions(tid(), ACTOR, ACTOR, List.of(R1)); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(r.log.isEmpty());
    }

    @Test void anUnknownUserOrRegionChangesNothing() throws Exception {
        Rec nu = new Rec(); nu.userExists = false;
        assertEquals("USER_NOT_FOUND", code(() -> { try { admin(nu).replaceRegions(tid(), ACTOR, USER, List.of(R1)); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(nu.of("update").isEmpty());
        Rec nr = new Rec(); nr.regionsExist = false;
        assertEquals("REGION_UNKNOWN", code(() -> { try { admin(nr).replaceRegions(tid(), ACTOR, USER, List.of(R1, R2)); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(nr.of("update").isEmpty(), "the old regions are kept when a new one is unknown");
    }

    @Test void theRegionSetIsReplacedInOneOrderedSequenceAndAudited() throws Exception {
        Rec r = new Rec();
        admin(r).replaceRegions(tid(), ACTOR, USER, Arrays.asList(R1, null, R2, R1));
        int lock = r.at("FROM users WHERE id = ? AND tenant_id = ? FOR UPDATE"), chk = r.at("FROM security_regions WHERE id = ?"), del = r.at("DELETE FROM security_user_regions"),
            ins = r.at("INSERT INTO security_user_regions"), aud = r.at("REGION_ACCESS_SET");
        assertTrue(lock >= 0 && lock < chk && chk < del && del < ins && ins < aud);
        List<Rec.Stmt> inserts = r.of("update").stream().filter(x -> x.sql().startsWith("INSERT INTO security_user_regions")).toList();
        assertEquals(2, inserts.size(), "a repeated or empty id is dropped");
        assertEquals(List.of(T, USER, R1, ACTOR), Arrays.asList(inserts.get(0).args()));
        assertEquals(List.of(T, USER, R2, ACTOR), Arrays.asList(inserts.get(1).args()));
        assertEquals(List.of(T, USER), Arrays.asList(r.log.get(del).args()));
        assertTrue(((String) r.log.get(aud).args()[3]).contains(R1.toString()) && ((String) r.log.get(aud).args()[3]).contains("\"regions\":2"));
    }

    @Test void anEmptyListOnlyRemovesTheRegionPart() throws Exception {
        Rec r = new Rec();
        admin(r).replaceRegions(tid(), ACTOR, USER, List.of());
        assertTrue(r.at("DELETE FROM security_user_regions") >= 0);
        assertEquals(-1, r.at("INSERT INTO security_user_regions"));
        assertEquals(-1, r.at("DELETE FROM security_user_scopes"), "client and site scope are not touched");
        assertTrue(r.of("update").stream().noneMatch(x -> x.sql().contains("security_branch_assignments")), "branch access is not touched");
    }

    @Test void tooManyRegionsAreRefusedBeforeAnyStatement() throws Exception {
        Rec r = new Rec();
        List<UUID> many = new ArrayList<>(); for (int i = 0; i <= SiteScopeRules.MAX_ITEMS; i++) many.add(new UUID(2, i));
        assertEquals("SCOPE_TOO_MANY", code(() -> { try { admin(r).replaceRegions(tid(), ACTOR, USER, many); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(r.log.isEmpty());
    }

    @Test void theOtherAccessPartsLeaveTheRegionsAlone() throws Exception {
        String sc = read("application/internal/SiteScopeService.java");
        assertFalse(sc.contains("DELETE FROM security_user_regions"), "SiteScopeService never deletes regions");
        String ad = read("application/internal/SiteAccessAdminService.java");
        int rb = ad.indexOf("public SiteScopeService.View replaceBranches"), rr = ad.indexOf("public SiteScopeService.View replaceRegions");
        assertFalse(ad.substring(rb, rr).contains("security_user_regions"), "replacing branches does not touch regions");
    }

    @Test void theRegionEndpointIsAdminOnlyFeatureGuardedAndWholeCompany() throws Exception {
        String c = read("api/SiteAccessAdminController.java");
        assertEquals(3, c.split("@(Get|Put)Mapping", -1).length - 1);
        assertEquals(3, c.split("hasAuthority\\('SECURITY_ADMIN'\\)", -1).length - 1);
        int at = c.indexOf(" setRegions(");
        String body = c.substring(at, c.indexOf("\n    }", at));
        assertTrue(body.indexOf("featureGuard") < body.indexOf("wholeCompany();") && body.indexOf("wholeCompany();") < body.indexOf("service."));
        assertTrue(body.contains("TenantContext.getCurrentUserId(), userId, r.regionIds()"));
        assertTrue(c.contains("\"/api/v1/security/users/{userId}/region-access\""));
    }
}
