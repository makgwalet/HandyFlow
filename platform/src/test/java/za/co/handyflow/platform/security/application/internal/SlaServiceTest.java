package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class SlaServiceTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID S1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID S2 = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final TenantId TENANT = TenantId.of(T);
    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    record Stmt(String kind, String sql, Object[] args) {}

    /** Records every statement and serves the rows the test sets up. Rows are maps by column label. */
    static class Rec extends JdbcTemplate {
        final List<Stmt> log = new ArrayList<>();
        final List<Map<String, Object>> targets = new ArrayList<>(), incidents = new ArrayList<>();
        int siteCount = 1, deleted = 1;

        @Override public void query(String s, RowCallbackHandler h, Object... a) {
            log.add(new Stmt("query", s, a));
            List<Map<String, Object>> rows = s.contains("security_sla_targets") ? targets : incidents;
            try { for (Map<String, Object> r : rows) h.processRow(rs(r)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        }
        @Override public <X> X queryForObject(String s, Class<X> t, Object... a) { log.add(new Stmt("one", s, a)); return t.cast(siteCount); }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return s.startsWith("DELETE") ? deleted : 1; }
        List<Stmt> updates() { return log.stream().filter(x -> x.kind().equals("update")).toList(); }
        Stmt incidentQuery() { return log.stream().filter(x -> x.kind().equals("query") && x.sql().contains("FROM security_incidents")).findFirst().orElseThrow(); }
    }

    private static java.sql.ResultSet rs(Map<String, Object> row) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(SlaServiceTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            Object v = a != null && a.length > 0 ? row.get(String.valueOf(a[0])) : null;
            return switch (m.getName()) {
                case "getInt" -> v == null ? 0 : v;
                case "getObject", "getString", "getTimestamp" -> v;
                default -> null;
            };
        });
    }

    private static SlaService svc(Rec r) throws Exception {
        var k = SlaService.class.getDeclaredConstructors()[0];
        SlaService s = (SlaService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r));
        set(s, "jdbc", r); set(s, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        return s;
    }
    private static void set(Object o, String name, Object v) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, v); }

    private static Map<String, Object> target(UUID site, String name, String sev, int ack, int res) {
        Map<String, Object> m = new HashMap<>(); m.put("site_id", site); m.put("name", name); m.put("severity", sev); m.put("acknowledge_minutes", ack); m.put("resolve_minutes", res); return m;
    }
    private static Map<String, Object> incident(int n, UUID site, String name, String sev, String title, String status, Instant created, Instant ack, Instant res) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", new UUID(7, n)); m.put("site_id", site); m.put("name", name); m.put("severity", sev); m.put("title", title); m.put("status", status);
        m.put("created_at", Timestamp.from(created)); m.put("acknowledged_at", ack == null ? null : Timestamp.from(ack)); m.put("resolved_at", res == null ? null : Timestamp.from(res));
        return m;
    }
    private static Instant ago(long min) { return NOW.minusSeconds(min * 60); }
    private static String codeOf(Callable<?> c) {
        try { c.call(); throw new AssertionError("nothing thrown"); } catch (HandyFlowException e) { return e.getErrorCode(); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static int marks(String s) { int n = 0; for (char c : s.toCharArray()) if (c == '?') n++; return n; }

    // ---- targets ----

    @Test void targetsSplitIntoCompanyDefaultsAndSitesInOrder() throws Exception {
        Rec r = new Rec();
        r.targets.add(target(S2, "Beta Mall", "LOW", 60, 600)); r.targets.add(target(null, null, "CRITICAL", 5, 60)); r.targets.add(target(S1, "Alpha Park", "HIGH", 10, 120));
        r.targets.add(target(null, null, "LOW", 60, 480)); r.targets.add(target(S1, "Alpha Park", "LOW", 30, 300));
        SlaService.TargetsView v = svc(r).targets(TENANT, null);
        assertEquals(List.of("LOW", "CRITICAL"), v.companyDefaults().stream().map(SlaService.TargetView::severity).toList());
        assertEquals(List.of("Alpha Park LOW", "Alpha Park HIGH", "Beta Mall LOW"), v.sites().stream().map(x -> x.siteName() + " " + x.severity()).toList());
        assertEquals(T, r.log.get(0).args()[0]);
    }

    @Test void aLimitedUserSeesOnlyTheirSitesButStillTheDefaults() throws Exception {
        Rec r = new Rec();
        r.targets.add(target(S2, "Beta", "LOW", 60, 600)); r.targets.add(target(S1, "Alpha", "HIGH", 10, 120)); r.targets.add(target(null, null, "LOW", 60, 480));
        SlaService.TargetsView v = svc(r).targets(TENANT, Set.of(S1));
        assertEquals(1, v.sites().size()); assertEquals(S1, v.sites().get(0).siteId());
        assertEquals(1, v.companyDefaults().size());
        assertTrue(svc(r).targets(TENANT, Set.of()).sites().isEmpty());
    }

    // ---- saving ----

    @Test void badInputIsRefusedBeforeAnythingIsWritten() throws Exception {
        Rec r = new Rec();
        assertEquals("SLA_SEVERITY", codeOf(() -> svc(r).save(TENANT, ACTOR, null, "URGENT", 5, 60)));
        assertEquals("SLA_MINUTES", codeOf(() -> svc(r).save(TENANT, ACTOR, null, "HIGH", 0, 60)));
        assertEquals("SLA_ORDER", codeOf(() -> svc(r).save(TENANT, ACTOR, null, "HIGH", 60, 5)));
        assertEquals(0, r.updates().size());
        assertTrue(r.log.isEmpty(), "not even a lookup");
    }

    @Test void aCompanyDefaultIsWrittenWithTheCompanyConflictTarget() throws Exception {
        Rec r = new Rec();
        svc(r).save(TENANT, ACTOR, null, " high ", 10, 120);
        Stmt up = r.updates().get(0);
        assertTrue(up.sql().contains("ON CONFLICT (tenant_id, severity) WHERE site_id IS NULL"));
        assertArrayEquals(new Object[]{T, "HIGH", 10, 120, ACTOR}, up.args());
        assertEquals(marks(up.sql()), up.args().length);
        Stmt au = r.updates().get(1);
        assertTrue(au.sql().contains("security_audit_log"));
        assertEquals("SLA_TARGET_SET", au.args()[3]);
        assertEquals(T, au.args()[2], "a company default is audited against the company");
        assertEquals(marks(au.sql()), au.args().length);
    }

    @Test void aSiteTargetChecksTheSiteThenUsesTheSiteConflictTarget() throws Exception {
        Rec r = new Rec();
        svc(r).save(TENANT, ACTOR, S1, "LOW", 30, 300);
        Stmt check = r.log.get(0);
        assertEquals("one", check.kind()); assertTrue(check.sql().contains("tenant_id = ?") && check.sql().contains("deleted_at IS NULL"));
        assertArrayEquals(new Object[]{S1, T}, check.args());
        Stmt up = r.updates().get(0);
        assertTrue(up.sql().contains("ON CONFLICT (tenant_id, site_id, severity) WHERE site_id IS NOT NULL"));
        assertArrayEquals(new Object[]{T, S1, "LOW", 30, 300, ACTOR}, up.args());
        assertEquals(marks(up.sql()), up.args().length);
        assertEquals(S1, r.updates().get(1).args()[2], "audited against the site");
    }

    @Test void aSiteThatIsNotTheCompanysIsNotFoundAndNothingIsWritten() throws Exception {
        Rec r = new Rec(); r.siteCount = 0;
        assertEquals("SITE_NOT_FOUND", codeOf(() -> svc(r).save(TENANT, ACTOR, S1, "LOW", 30, 300)));
        assertEquals(0, r.updates().size());
    }

    // ---- removing ----

    @Test void removingATargetDeletesItAndIsAudited() throws Exception {
        Rec r = new Rec();
        svc(r).remove(TENANT, ACTOR, S1, "low");
        assertTrue(r.updates().get(0).sql().startsWith("DELETE FROM security_sla_targets WHERE tenant_id = ? AND site_id = ? AND severity = ?"));
        assertArrayEquals(new Object[]{T, S1, "LOW"}, r.updates().get(0).args());
        assertEquals("SLA_TARGET_REMOVED", r.updates().get(1).args()[3]);
        Rec r2 = new Rec();
        svc(r2).remove(TENANT, ACTOR, null, "LOW");
        assertTrue(r2.updates().get(0).sql().contains("site_id IS NULL"));
        assertArrayEquals(new Object[]{T, "LOW"}, r2.updates().get(0).args());
    }

    @Test void removingATargetThatIsNotThereIsNotFoundAndNotAudited() throws Exception {
        Rec r = new Rec(); r.deleted = 0;
        assertEquals("SLA_NOT_FOUND", codeOf(() -> svc(r).remove(TENANT, ACTOR, S1, "LOW")));
        assertEquals(1, r.updates().size());
        assertEquals("SLA_SEVERITY", codeOf(() -> svc(r).remove(TENANT, ACTOR, S1, "nope")));
    }

    // ---- live ----

    @Test void liveListsOnlyBreachedAndAtRiskOpenIncidentsWorstFirstUsingTheSiteTargetFirst() throws Exception {
        Rec r = new Rec();
        r.targets.add(target(null, null, "HIGH", 10, 120)); r.targets.add(target(S2, "Beta", "HIGH", 60, 600));
        r.incidents.add(incident(1, S1, "Alpha", "HIGH", "Break-in", "OPEN", ago(30), null, null));            // default 10 min: 20 overdue
        r.incidents.add(incident(2, S1, "Alpha", "HIGH", "Alarm", "OPEN", ago(5), null, null));                 // 5 of 10: pending
        r.incidents.add(incident(3, S2, "Beta", "HIGH", "Fence", "OPEN", ago(30), null, null));                 // site target 60: pending
        r.incidents.add(incident(4, S1, "Alpha", "HIGH", "Slow", "ACKNOWLEDGED", ago(110), ago(100), null));    // resolve due in 10 of 120: at risk
        r.incidents.add(incident(5, S1, "Alpha", "LOW", "Untracked", "OPEN", ago(500), null, null));            // no LOW target
        List<SlaService.LiveItem> live = svc(r).live(TENANT, null);
        assertEquals(List.of("Break-in", "Slow"), live.stream().map(SlaService.LiveItem::title).toList());
        SlaService.LiveItem first = live.get(0);
        assertEquals("BREACHED", first.state()); assertEquals("ACKNOWLEDGE", first.stage()); assertEquals(20L, first.overdueMinutes()); assertNull(first.remainingMinutes());
        assertEquals(ago(30), first.recordedAt()); assertEquals("OPEN", first.status()); assertEquals("Alpha", first.siteName());
        SlaService.LiveItem second = live.get(1);
        assertEquals("AT_RISK", second.state()); assertEquals("RESOLVE", second.stage()); assertEquals(10L, second.remainingMinutes());
    }

    @Test void liveAsksOnlyForOpenIncidentsOfThisCompanyThatAreNotDeleted() throws Exception {
        Rec r = new Rec(); svc(r).live(TENANT, null);
        Stmt q = r.incidentQuery();
        assertTrue(q.sql().contains("i.tenant_id = ?") && q.sql().contains("i.deleted_at IS NULL") && q.sql().contains("i.resolved_at IS NULL"));
        assertTrue(q.sql().contains("s.tenant_id = i.tenant_id"));
        assertArrayEquals(new Object[]{T}, q.args());
        assertEquals(marks(q.sql()), q.args().length);
    }

    @Test void aLimitedUserSeesOnlyTheirSitesInTheLiveList() throws Exception {
        Rec r = new Rec(); r.targets.add(target(null, null, "HIGH", 10, 120));
        r.incidents.add(incident(1, S1, "Alpha", "HIGH", "Mine", "OPEN", ago(30), null, null)); r.incidents.add(incident(2, S2, "Beta", "HIGH", "Theirs", "OPEN", ago(30), null, null));
        assertEquals(List.of("Mine"), svc(r).live(TENANT, Set.of(S1)).stream().map(SlaService.LiveItem::title).toList());
        assertEquals(2, svc(r).live(TENANT, null).size());
        assertEquals(0, svc(r).live(TENANT, Set.of()).size());
    }

    @Test void aHugeOpenListIsRefusedEvenWhenTheUserSeesLittleOfIt() throws Exception {
        Rec r = new Rec();
        for (int i = 0; i <= SlaService.MAX_LIVE; i++) r.incidents.add(incident(i, S2, "Beta", "HIGH", "x", "OPEN", ago(1), null, null));
        assertEquals("SLA_TOO_MANY", codeOf(() -> svc(r).live(TENANT, Set.of(S1))));
    }

    // ---- report ----

    @Test void theReportCutsDaysInTheCompanyZoneAndIsHalfOpen() throws Exception {
        Rec r = new Rec();
        svc(r).report(TENANT, null, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), null);
        Stmt q = r.incidentQuery();
        assertEquals(Timestamp.from(Instant.parse("2026-10-31T22:00:00Z")), q.args()[1]);
        assertEquals(Timestamp.from(Instant.parse("2026-11-30T22:00:00Z")), q.args()[2]);
        assertTrue(q.sql().contains("i.created_at >= ? AND i.created_at < ?"));
        assertEquals(marks(q.sql()), q.args().length);
        assertFalse(q.sql().contains("i.site_id = ?"));
    }

    @Test void aSiteFilterIsAnExtraArgumentInOrder() throws Exception {
        Rec r = new Rec();
        svc(r).report(TENANT, null, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), S1);
        Stmt q = r.incidentQuery();
        assertTrue(q.sql().contains("AND i.site_id = ?"));
        assertEquals(S1, q.args()[3]); assertEquals(4, q.args().length); assertEquals(marks(q.sql()), q.args().length);
    }

    @Test void badRangesAreRefused() throws Exception {
        Rec r = new Rec();
        LocalDate d = LocalDate.parse("2026-11-01");
        assertEquals("SLA_RANGE", codeOf(() -> svc(r).report(TENANT, null, null, d, null)));
        assertEquals("SLA_RANGE", codeOf(() -> svc(r).report(TENANT, null, d, null, null)));
        assertEquals("SLA_RANGE", codeOf(() -> svc(r).report(TENANT, null, d, d.minusDays(1), null)));
        assertEquals("SLA_RANGE", codeOf(() -> svc(r).report(TENANT, null, d, d.plusDays(366), null)));
        svc(r).report(TENANT, null, d, d.plusDays(365), null); // 366 days is allowed
    }

    @Test void theReportCountsMetBreachedOpenAndUntracked() throws Exception {
        Rec r = new Rec();
        r.targets.add(target(null, null, "HIGH", 10, 120));
        r.incidents.add(incident(1, S1, "Alpha", "HIGH", "a", "RESOLVED", ago(600), ago(595), ago(540)));   // met
        r.incidents.add(incident(2, S1, "Alpha", "HIGH", "b", "RESOLVED", ago(600), ago(560), ago(500)));   // acknowledged late
        r.incidents.add(incident(3, S1, "Alpha", "HIGH", "c", "OPEN", ago(2), null, null));                 // pending
        r.incidents.add(incident(4, S1, "Alpha", "LOW", "d", "OPEN", ago(900), null, null));                // untracked
        SlaService.Report rep = svc(r).report(TENANT, null, LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-23"), null);
        assertEquals(4, rep.total().total()); assertEquals(3, rep.total().tracked()); assertEquals(1, rep.total().met()); assertEquals(1, rep.total().breached()); assertEquals(1, rep.total().pending());
        assertEquals(50.0, rep.total().compliancePct());
        assertEquals(2, rep.rows().size());
        assertEquals("LOW", rep.rows().get(0).severity()); assertNull(rep.rows().get(0).compliancePct());
        assertEquals("HIGH", rep.rows().get(1).severity());
    }

    @Test void aLimitedUsersReportCountsOnlyTheirSites() throws Exception {
        Rec r = new Rec(); r.targets.add(target(null, null, "HIGH", 10, 120));
        r.incidents.add(incident(1, S1, "Alpha", "HIGH", "a", "OPEN", ago(2), null, null)); r.incidents.add(incident(2, S2, "Beta", "HIGH", "b", "OPEN", ago(2), null, null));
        SlaService.Report rep = svc(r).report(TENANT, Set.of(S1), LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-23"), null);
        assertEquals(1, rep.total().total());
        assertEquals(List.of(S1), rep.rows().stream().map(SlaRules.Row::siteId).toList());
    }

    @Test void aRangeWithTooManyIncidentsIsRefused() throws Exception {
        Rec r = new Rec();
        for (int i = 0; i <= SlaService.MAX_ROWS; i++) r.incidents.add(incident(i, S1, "Alpha", "HIGH", "x", "OPEN", ago(1), null, null));
        assertEquals("SLA_TOO_MANY", codeOf(() -> svc(r).report(TENANT, null, LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-23"), null)));
    }

    // ---- wiring ----

    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    @Test void theClockStartsWhenTheIncidentWasRecordedNotWhenTheGuardSaysItHappened() throws Exception {
        String s = read("application/internal/SlaService.java");
        assertTrue(s.contains("i.created_at"));
        assertFalse(s.contains("i.occurred_at"), "the guard-entered time can be backdated");
        assertTrue(s.contains("rs.getTimestamp(\"created_at\")"));
    }

    @Test void readsNeedReadWritesNeedManageAndWritesAreWholeCompanyOnly() throws Exception {
        String s = read("api/SlaController.java");
        for (String[] m : new String[][]{{"@GetMapping(\"/targets\")", "SECURITY_READ"}, {"@PutMapping(\"/targets\")", "SECURITY_MANAGE"}, {"@DeleteMapping(\"/targets\")", "SECURITY_MANAGE"},
                {"@GetMapping(\"/live\")", "SECURITY_READ"}, {"@GetMapping(\"/report\")", "SECURITY_READ"}}) {
            int at = s.indexOf(m[0]); assertTrue(at > 0, m[0]);
            assertTrue(s.substring(at, s.indexOf("public ", at)).contains("hasAuthority('" + m[1] + "')"), m[0]);
        }
        assertEquals(2, s.split("SiteScopeService.checkUnrestricted\\(scope, t, user\\)", -1).length - 1);
        assertTrue(s.contains("SiteScopeService.checkChoice(scope, t, user, siteId)"));
        assertEquals(3, s.split("SiteScopeService.allowedSites\\(scope, t, ", -1).length - 1, "targets, live and report are all limited to the user's sites");
    }

    @Test void theMigrationHasOneTargetPerSeverityAndSiteAndOneDefaultPerSeverity() throws Exception {
        String s = Files.readString(Path.of("src/main/resources/db/migration/V867__security_sla_targets.sql"));
        assertTrue(s.contains("ux_sla_targets_site ON security_sla_targets (tenant_id, site_id, severity) WHERE site_id IS NOT NULL"));
        assertTrue(s.contains("ux_sla_targets_company ON security_sla_targets (tenant_id, severity) WHERE site_id IS NULL"));
        assertTrue(s.contains("resolve_minutes >= acknowledge_minutes"));
        assertTrue(s.contains("hf_rls_apply"));
    }
}
