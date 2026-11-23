package za.co.handyflow.platform.identity.application.internal;

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

class SsoPolicyServiceTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID G1 = UUID.fromString("33333333-3333-3333-3333-333333333332");
    private static final UUID G2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/identity/";

    static class Rec extends JdbcTemplate {
        record Stmt(String kind, String sql, Object[] args) {}
        final List<Stmt> log = new ArrayList<>();
        boolean usersActive = true, hasSettings = false, enabled = true, enforce = true, isGlass = false;
        String domains = "acme.co.za", actorEmail = "me@other.co.za";
        List<UUID> glassRows = new ArrayList<>();
        @Override public <X> List<X> queryForList(String s, Class<X> t, Object... a) {
            log.add(new Stmt("list", s, a));
            List<X> out = new ArrayList<>();
            if (s.contains("FROM tenant_sso_break_glass")) for (UUID u : glassRows) out.add(t.cast(u));
            if (s.startsWith("SELECT email FROM users")) out.add(t.cast(actorEmail));
            return out;
        }
        @Override public <X> X queryForObject(String s, Class<X> t, Object... a) {
            log.add(new Stmt("one", s, a));
            if (s.contains("FROM users")) return t.cast(usersActive ? 1 : 0);
            if (s.contains("FROM tenant_sso_break_glass")) return t.cast(isGlass ? 1 : 0);
            return null;
        }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return 1; }
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            log.add(new Stmt("query", s, a));
            List<X> out = new ArrayList<>();
            try { if (hasSettings) out.add(m.mapRow(row(), 0)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
        List<Stmt> of(String kind) { return log.stream().filter(x -> x.kind().equals(kind)).toList(); }
        int at(String frag) { for (int i = 0; i < log.size(); i++) if (log.get(i).sql().contains(frag)) return i; return -1; }
        Rec glass(UUID... u) { glassRows = new ArrayList<>(Arrays.asList(u)); return this; }
        Object row;
        java.sql.ResultSet row() {
            Rec me = this;
            return (java.sql.ResultSet) Proxy.newProxyInstance(SsoPolicyServiceTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
                String label = a != null && a.length > 0 ? String.valueOf(a[0]) : "";
                return switch (m.getName()) {
                    case "getBoolean" -> label.equals("enabled") ? me.enabled : me.enforce;
                    case "getString" -> switch (label) { case "provider" -> "OIDC"; case "issuer_url" -> "https://login.example.com"; case "client_id" -> "cid"; case "client_secret_ref" -> "ACME_SECRET"; case "domains" -> me.domains; default -> null; };
                    default -> null;
                };
            });
        }
    }

    /** Records what would have been audited. Built the way the real class is (it needs a JdbcTemplate), without using it. */
    static class Audit extends AccessAuditService {
        final List<Object[]> events = new ArrayList<>();
        Audit() { super(null); }
        @Override public void record(TenantId t, String action, String targetType, UUID targetId, String label, Map<String, ?> before, Map<String, ?> after) {
            events.add(new Object[]{t, action, targetType, targetId, label, before, after});
        }
    }

    private static SsoPolicyService svc(Rec r, AccessAuditService audit) throws Exception {
        var k = SsoPolicyService.class.getDeclaredConstructors()[0];
        SsoPolicyService s = (SsoPolicyService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r));
        set(s, "jdbc", r); if (audit != null) set(s, "audit", audit);
        return s;
    }
    private static void set(Object o, String name, Object v) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, v); }
    private static TenantId tid() { return TenantId.of(T); }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    private static SsoPolicyService.Request req(boolean enabled, boolean enforce, List<UUID> glass) {
        return new SsoPolicyService.Request(enabled, " https://login.example.com ", " cid ", "ACME_SECRET", List.of("ACME.co.za", "@acme.com"), enforce, glass);
    }
    private static String code(ThrowingRun r) {
        return assertThrows(HandyFlowException.class, () -> { try { r.run(); } catch (HandyFlowException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }).getErrorCode();
    }
    interface ThrowingRun { void run() throws Exception; }

    // ---- reading ----

    @Test void noSettingsMeansOffWithNothingInIt() throws Exception {
        Rec r = new Rec();
        SsoPolicyService.Settings s = svc(r, null).get(tid());
        assertFalse(s.enabled()); assertFalse(s.enforce()); assertEquals(List.of(), s.allowedDomains()); assertEquals(List.of(), s.breakGlass()); assertNull(s.issuerUrl());
        for (Rec.Stmt q : r.log) assertEquals(T, q.args()[0], "both reads are for this company");
    }

    @Test void savedSettingsAreReadBackWithDomainsAndBreakGlass() throws Exception {
        Rec r = new Rec(); r.hasSettings = true; r.domains = "acme.co.za,acme.com"; r.glass(G1, G2);
        SsoPolicyService.Settings s = svc(r, null).get(tid());
        assertEquals(List.of("acme.co.za", "acme.com"), s.allowedDomains()); assertEquals(List.of(G1, G2), s.breakGlass());
        assertEquals("ACME_SECRET", s.clientSecretRef()); assertTrue(s.enabled());
    }

    // ---- saving ----

    @Test void savingValidatesEverythingBeforeItWritesAnything() throws Exception {
        for (SsoPolicyService.Request bad : List.of(
                new SsoPolicyService.Request(true, "http://login.example.com", "cid", null, List.of("acme.co.za"), false, List.of()),
                new SsoPolicyService.Request(true, "https://login.example.com", "", null, List.of("acme.co.za"), false, List.of()),
                new SsoPolicyService.Request(true, "https://login.example.com", "cid", "bad ref", List.of("acme.co.za"), false, List.of()),
                new SsoPolicyService.Request(true, "https://login.example.com", "cid", null, List.of("gmail.com"), false, List.of()),
                new SsoPolicyService.Request(true, "https://login.example.com", "cid", null, List.of(), false, List.of()),
                new SsoPolicyService.Request(false, "https://login.example.com", "cid", null, List.of(), true, List.of(G1)))) {
            Rec r = new Rec();
            assertThrows(HandyFlowException.class, () -> svc(r, null).save(tid(), ACTOR, bad));
            assertTrue(r.of("update").isEmpty(), "nothing is written for " + bad);
        }
    }

    @Test void theCodesForTheTwoCombinationRules() throws Exception {
        Rec r = new Rec();
        assertEquals("SSO_DOMAIN_REQUIRED", code(() -> svc(r, null).save(tid(), ACTOR, new SsoPolicyService.Request(true, "https://login.example.com", "cid", null, List.of(), false, List.of()))));
        assertEquals("SSO_ENFORCE_NEEDS_ENABLED", code(() -> svc(r, null).save(tid(), ACTOR, new SsoPolicyService.Request(false, null, null, null, List.of(), true, List.of()))));
    }

    @Test void aBreakGlassPersonWhoIsNotAnActivePersonOfThisCompanyIsRefused() throws Exception {
        Rec r = new Rec(); r.usersActive = false;
        assertEquals("SSO_BREAK_GLASS_UNKNOWN", code(() -> svc(r, null).save(tid(), ACTOR, req(true, false, List.of(G1)))));
        assertTrue(r.of("update").isEmpty());
        Rec.Stmt q = r.of("one").get(0);
        assertEquals(List.of(G1, T), Arrays.asList(q.args())); assertTrue(q.sql().contains("status = 'ACTIVE'") && q.sql().contains("tenant_id = ?"));
    }

    @Test void enforcingWithoutBreakGlassOrLockingYourselfOutIsAConflictAndWritesNothing() throws Exception {
        Rec none = new Rec();
        assertEquals("SSO_LOCKOUT", code(() -> svc(none, null).save(tid(), ACTOR, req(true, true, List.of()))));
        assertTrue(none.of("update").isEmpty());
        Rec self = new Rec(); self.actorEmail = "me@acme.co.za";
        assertEquals("SSO_LOCKOUT", code(() -> svc(self, null).save(tid(), ACTOR, req(true, true, List.of(G1)))));
        assertTrue(self.of("update").isEmpty());
        Rec ok = new Rec(); ok.actorEmail = "me@acme.co.za";
        svc(ok, null).save(tid(), ACTOR, req(true, true, List.of(ACTOR, G1)));
        assertFalse(ok.of("update").isEmpty());
    }

    @Test void aSaveRunsInOneOrderedSequenceWithCleanedValues() throws Exception {
        Rec r = new Rec(); r.hasSettings = true;
        Audit audit = new Audit();
        svc(r, audit).save(tid(), ACTOR, req(true, true, List.of(G1, G2)));
        int users = r.at("FROM users WHERE id = ?"), email = r.at("SELECT email FROM users"), up = r.at("INSERT INTO tenant_sso_settings"), del = r.at("DELETE FROM tenant_sso_break_glass"), ins = r.at("INSERT INTO tenant_sso_break_glass");
        assertTrue(users >= 0 && users < email && email < up && up < del && del < ins);
        Object[] a = r.log.get(up).args();
        assertEquals(List.of(T, true, "https://login.example.com", "cid", "ACME_SECRET", "acme.co.za,acme.com", true, ACTOR), Arrays.asList(a));
        assertEquals(List.of(T), Arrays.asList(r.log.get(del).args()));
        assertEquals(2L, r.of("update").stream().filter(x -> x.sql().startsWith("INSERT INTO tenant_sso_break_glass")).count());
        assertEquals(1, audit.events.size());
        assertEquals("SSO_SETTINGS_CHANGED", audit.events.get(0)[1]);
        assertEquals(T, audit.events.get(0)[3]);
        assertTrue(r.of("update").stream().noneMatch(x -> Arrays.stream(x.args()).anyMatch(v -> v instanceof String s && s.toLowerCase().contains("secret value"))));
    }

    @Test void aDisabledSaveMayLeaveTheProviderFieldsEmpty() throws Exception {
        Rec r = new Rec();
        svc(r, null).save(tid(), ACTOR, new SsoPolicyService.Request(false, " ", null, "", null, false, null));
        Object[] a = r.log.get(r.at("INSERT INTO tenant_sso_settings")).args();
        assertEquals(Arrays.asList(T, false, null, null, null, "", false, ACTOR), Arrays.asList(a));
        assertEquals(-1, r.at("INSERT INTO tenant_sso_break_glass"));
    }

    @Test void theAuditNeverCarriesAnythingSecret() throws Exception {
        Rec r = new Rec(); r.hasSettings = true;
        Audit audit = new Audit();
        svc(r, audit).save(tid(), ACTOR, req(true, false, List.of()));
        Map<?, ?> after = (Map<?, ?>) audit.events.get(0)[6];
        assertEquals(Set.of("enabled", "enforce", "issuerUrl", "clientId", "clientSecretRef", "allowedDomains", "breakGlassCount"), after.keySet());
    }

    // ---- the sign-in check ----

    @Test void aCompanyWithoutSettingsIsNeverAskedAnythingElse() throws Exception {
        Rec r = new Rec(); r.hasSettings = false;
        svc(r, null).requirePasswordAllowed(tid(), G1, "a@acme.co.za");
        assertEquals(1, r.log.size());
        assertEquals(List.of(T), Arrays.asList(r.log.get(0).args()));
    }

    @Test void whereItIsEnforcedAPasswordIsRefusedWith403() throws Exception {
        Rec r = new Rec(); r.hasSettings = true;
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc(r, null).requirePasswordAllowed(tid(), G1, "thandi@acme.co.za"));
        assertEquals("SSO_REQUIRED", e.getErrorCode()); assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals(List.of(T, G1), Arrays.asList(r.of("one").get(0).args()), "the break-glass lookup is for this company and this person");
    }

    @Test void aBreakGlassPersonKeepsTheirPassword() throws Exception {
        Rec r = new Rec(); r.hasSettings = true; r.isGlass = true;
        svc(r, null).requirePasswordAllowed(tid(), G1, "thandi@acme.co.za");
    }

    @Test void otherDomainsOffOrUnenforcedAreNotAskedForBreakGlass() throws Exception {
        for (Rec r : new Rec[]{ new Rec(), new Rec(), new Rec() }) r.hasSettings = true;
        Rec other = new Rec(); other.hasSettings = true;
        svc(other, null).requirePasswordAllowed(tid(), G1, "contractor@other.co.za");
        Rec off = new Rec(); off.hasSettings = true; off.enabled = false;
        svc(off, null).requirePasswordAllowed(tid(), G1, "thandi@acme.co.za");
        Rec soft = new Rec(); soft.hasSettings = true; soft.enforce = false;
        svc(soft, null).requirePasswordAllowed(tid(), G1, "thandi@acme.co.za");
        for (Rec r : new Rec[]{other, off, soft}) assertTrue(r.of("one").isEmpty());
    }

    // ---- wiring and source checks ----

    @Test void theLoginChecksSsoAfterThePasswordAndTheActiveCheckAndBeforeIssuingAToken() throws Exception {
        String a = read("application/internal/AuthService.java");
        int login = a.indexOf("public AuthResponse login(");
        String body = a.substring(login, a.indexOf("private AuthResponse buildAuthResponse"));
        int pw = body.indexOf("passwordEncoder.matches"), active = body.indexOf("user.isActive()"), sso = body.indexOf("ssoPolicy.requirePasswordAllowed(tenant.getTenantId(), user.getId(), user.getEmail())"), build = body.indexOf("return buildAuthResponse");
        assertTrue(pw > 0 && pw < active && active < sso && sso < build);
        assertTrue(a.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private SsoPolicyService ssoPolicy;"));
        assertTrue(body.contains("if (ssoPolicy != null)"));
    }

    @Test void theControllerNeedsSettingsManageAndTakesTheCompanyAndUserFromTheSession() throws Exception {
        String c = read("api/SsoController.java");
        assertEquals(2, c.split("@(Get|Put)Mapping", -1).length - 1);
        assertEquals(2, c.split("hasAuthority\\('SETTINGS_MANAGE'\\)", -1).length - 1);
        assertTrue(c.contains("service.save(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), r)"));
        assertTrue(c.contains("service.get(TenantContext.getTenantIdAsObject())"));
        assertFalse(c.contains("tenantId\""));
    }

    @Test void everyStatementIsForOneCompanyAndNoSecretHasAColumn() throws Exception {
        String s = read("application/internal/SsoPolicyService.java");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("jdbc\\.(update|query|queryForList|queryForObject)\\(").matcher(s);
        int n = 0;
        while (m.find()) {
            String call = s.substring(m.start(), s.indexOf(");", m.start()));
            assertTrue(call.contains("tenant_id") || call.contains("SETTINGS,"), "no company in: " + call); n++;
        }
        assertTrue(n >= 8, "found " + n);
        assertTrue(s.contains("WHERE tenant_id = ?"));
        String m1 = Files.readString(Path.of("src/main/resources/db/migration/V869__tenant_sso_settings.sql"));
        assertFalse(m1.matches("(?s).*\\bclient_secret\\s+(VARCHAR|TEXT|BYTEA).*"), "only a reference to a secret is stored");
        assertTrue(m1.contains("client_secret_ref"));
        assertTrue(m1.contains("REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(m1.contains("CHECK (NOT enforce OR enabled)"));
    }
}