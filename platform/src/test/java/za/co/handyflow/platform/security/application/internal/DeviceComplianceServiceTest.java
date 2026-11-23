package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeviceComplianceServiceTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID G1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID G2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID D1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID D2 = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final UUID D3 = UUID.fromString("44444444-4444-4444-4444-444444444443");
    private static final UUID D4 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    static class Rec extends JdbcTemplate {
        record Stmt(String kind, String sql, Object[] args) {}
        final List<Stmt> log = new ArrayList<>();
        Map<String, Object> policy;                       // null = no policy row
        List<Map<String, Object>> deviceLookup = new ArrayList<>();
        List<Map<String, Object>> devices = new ArrayList<>();

        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            log.add(new Stmt("query", s, a));
            List<Map<String, Object>> rows;
            if (s.contains("FROM security_device_policy") && s.contains("SELECT updated_at")) rows = policy == null ? List.of() : List.of(policy);
            else if (s.contains("FROM security_device_policy")) rows = policy == null ? List.of() : List.of(policy);
            else if (s.contains("SELECT id, guard_id, status")) rows = deviceLookup;
            else if (s.contains("FROM security_devices d")) rows = devices;
            else throw new IllegalStateException("unexpected query: " + s);
            List<X> out = new ArrayList<>();
            try { for (int i = 0; i < rows.size(); i++) out.add(m.mapRow(rs(rows.get(i)), i)); }
            catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return 1; }
        List<Stmt> writes() { return log.stream().filter(x -> x.kind().equals("update")).toList(); }
    }

    private static java.sql.ResultSet rs(Map<String, Object> row) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(DeviceComplianceServiceTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            String label = a != null && a.length > 0 ? String.valueOf(a[0]) : "";
            Object v = row.get(label);
            return switch (m.getName()) {
                case "getBoolean" -> Boolean.TRUE.equals(v);
                case "getTimestamp" -> v;
                default -> v;
            };
        });
    }

    private static Map<String, Object> policyRow(String app, Integer os, boolean lock, boolean root) {
        Map<String, Object> m = new HashMap<>();
        m.put("min_app_version", app); m.put("min_os_major", os); m.put("require_screen_lock", lock); m.put("block_rooted", root);
        m.put("updated_at", Timestamp.from(Instant.parse("2026-11-01T08:00:00Z")));
        return m;
    }

    private static Map<String, Object> dev(UUID id, String name, UUID guard, String first, String last, String app, String os, Boolean lock, Boolean root) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id); m.put("device_name", name); m.put("device_type", guard == null ? "SHARED_SITE_DEVICE" : "PERSONAL_GUARD_DEVICE");
        m.put("guard_id", guard); m.put("first_name", first); m.put("last_name", last); m.put("app_version", app); m.put("os_version", os);
        m.put("screen_lock_on", lock); m.put("rooted", root); m.put("posture_reported_at", null);
        return m;
    }

    private static Map<String, Object> lookup(UUID id, UUID guard, String status) {
        Map<String, Object> m = new HashMap<>(); m.put("id", id); m.put("guard_id", guard); m.put("status", status); return m;
    }

    private static DeviceComplianceService svc(Rec r) throws Exception {
        var k = DeviceComplianceService.class.getDeclaredConstructors()[0];
        DeviceComplianceService s = (DeviceComplianceService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r));
        Field f = DeviceComplianceService.class.getDeclaredField("jdbc"); f.setAccessible(true); f.set(s, r);
        return s;
    }
    private interface Call { void run() throws Exception; }
    private static String code(Call c) { return assertThrows(HandyFlowException.class, () -> c.run()).getErrorCode(); }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static TenantId tid() { return TenantId.of(T); }

    // ---- policy ----

    @Test void noPolicyRowReadsAsAnEmptyPolicy() throws Exception {
        var v = svc(new Rec()).policy(tid());
        assertNull(v.minAppVersion());
        assertNull(v.minOsMajor());
        assertFalse(v.requireScreenLock());
        assertFalse(v.blockRooted());
        assertFalse(v.asksAnything());
        assertNull(v.updatedAt());
    }

    @Test void aStoredPolicyIsReadBack() throws Exception {
        Rec r = new Rec(); r.policy = policyRow("2.4", 12, true, false);
        var v = svc(r).policy(tid());
        assertEquals("2.4", v.minAppVersion());
        assertEquals(12, v.minOsMajor());
        assertTrue(v.requireScreenLock());
        assertFalse(v.blockRooted());
        assertTrue(v.asksAnything());
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), v.updatedAt());
    }

    @Test void savingCleansThenUpsertsThenAudits() throws Exception {
        Rec r = new Rec(); r.policy = policyRow(null, null, false, false);
        svc(r).savePolicy(tid(), ACTOR, " 2.4 ", 12, true, null);
        assertEquals(2, r.writes().size());
        Rec.Stmt up = r.writes().get(0);
        assertTrue(up.sql().contains("INSERT INTO security_device_policy"));
        assertTrue(up.sql().contains("ON CONFLICT (tenant_id) DO UPDATE"));
        assertArrayEquals(new Object[]{T, "2.4", 12, true, false, ACTOR}, up.args());
        Rec.Stmt au = r.writes().get(1);
        assertTrue(au.sql().contains("security_audit_log"));
        assertEquals(T, au.args()[0]);
        assertEquals(ACTOR, au.args()[1]);
        assertEquals(T, au.args()[2]);
        assertTrue(au.sql().contains("'DEVICE_POLICY_CHANGED'"));
        assertEquals("app>=-, os>=-, screenLock=false, noRoot=false", au.args()[3], "before");
        assertEquals("app>=2.4, os>=12, screenLock=true, noRoot=false", au.args()[4], "after");
    }

    @Test void theBeforeValueIsReadBeforeTheWrite() throws Exception {
        Rec r = new Rec(); r.policy = policyRow("1.0", null, false, false);
        svc(r).savePolicy(tid(), ACTOR, "2.0", null, null, null);
        int firstRead = -1, firstWrite = -1;
        for (int i = 0; i < r.log.size(); i++) {
            if (firstRead < 0 && r.log.get(i).kind().equals("query")) firstRead = i;
            if (firstWrite < 0 && r.log.get(i).kind().equals("update")) firstWrite = i;
        }
        assertTrue(firstRead >= 0 && firstRead < firstWrite);
        assertEquals("app>=1.0, os>=-, screenLock=false, noRoot=false", r.writes().get(1).args()[3]);
    }

    @Test void aBadPolicyStopsBeforeAnyStatement() throws Exception {
        Rec r = new Rec();
        assertEquals("DEVICE_POLICY_APP_VERSION", code(() -> svc(r).savePolicy(tid(), ACTOR, "2.x", null, null, null)));
        assertEquals("DEVICE_POLICY_OS_VERSION", code(() -> svc(r).savePolicy(tid(), ACTOR, null, 0, null, null)));
        assertTrue(r.log.isEmpty());
    }

    // ---- the phone's report ----

    @Test void aPhoneReportsItsOwnPosture() throws Exception {
        Rec r = new Rec(); r.deviceLookup.add(lookup(D1, G1, "ACTIVE"));
        svc(r).reportPosture(tid(), G1, "hw-1", " Android 14 ", true, false);
        assertEquals(1, r.writes().size());
        Rec.Stmt u = r.writes().get(0);
        assertTrue(u.sql().contains("UPDATE security_devices"));
        assertTrue(u.sql().contains("COALESCE(?, os_version)"));
        assertTrue(u.sql().contains("COALESCE(?, screen_lock_on)"));
        assertTrue(u.sql().contains("COALESCE(?, rooted)"));
        assertTrue(u.sql().contains("tenant_id = ?"));
        assertArrayEquals(new Object[]{"Android 14", true, false, D1, T}, u.args());
    }

    @Test void junkOsIsDroppedAndTheRestStillRecorded() throws Exception {
        Rec r = new Rec(); r.deviceLookup.add(lookup(D1, G1, "ACTIVE"));
        svc(r).reportPosture(tid(), G1, "hw-1", "<b>x</b>", null, true);
        assertArrayEquals(new Object[]{null, null, true, D1, T}, r.writes().get(0).args());
    }

    @Test void aSharedPhoneMayBeReportedByAnyGuard() throws Exception {
        Rec r = new Rec(); r.deviceLookup.add(lookup(D1, null, "ACTIVE"));
        svc(r).reportPosture(tid(), G2, "hw-1", "13", null, null);
        assertEquals(1, r.writes().size());
    }

    @Test void anotherGuardsPhoneLooksLikeItDoesNotExist() throws Exception {
        Rec r = new Rec(); r.deviceLookup.add(lookup(D1, G1, "ACTIVE"));
        assertThrows(ResourceNotFoundException.class, () -> svc(r).reportPosture(tid(), G2, "hw-1", "13", true, false));
        assertTrue(r.writes().isEmpty());
    }

    @Test void anUnknownPhoneIsNotFound() throws Exception {
        Rec r = new Rec();
        assertThrows(ResourceNotFoundException.class, () -> svc(r).reportPosture(tid(), G1, "nope", "13", true, false));
        assertTrue(r.writes().isEmpty());
    }

    @Test void aPhoneOutOfServiceMayNotReport() throws Exception {
        for (String st : List.of("LOST", "REVOKED", "BLOCKED", "PENDING", "REPLACED", "DECOMMISSIONED")) {
            Rec r = new Rec(); r.deviceLookup.add(lookup(D1, G1, st));
            assertEquals("DEVICE_INACTIVE", code(() -> svc(r).reportPosture(tid(), G1, "hw-1", "13", true, false)), st);
            assertTrue(r.writes().isEmpty());
        }
    }

    @Test void theLookupIsByCompanyAndHardwareId() throws Exception {
        Rec r = new Rec(); r.deviceLookup.add(lookup(D1, G1, "ACTIVE"));
        svc(r).reportPosture(tid(), G1, "hw-1", "13", true, false);
        var q = r.log.get(0);
        assertTrue(q.sql().contains("tenant_id = ?") && q.sql().contains("device_hardware_id = ?"));
        assertArrayEquals(new Object[]{T, "hw-1"}, q.args());
    }

    // ---- the report ----

    @Test void withNoPolicyEveryActivePhoneIsCompliantAndNothingIsListed() throws Exception {
        Rec r = new Rec();
        r.devices.add(dev(D1, "A", null, null, null, "1.0", null, null, null));
        r.devices.add(dev(D2, "B", G1, "Thandi", "Nkosi", null, null, null, null));
        var rep = svc(r).report(tid());
        assertEquals(2, rep.activeDevices());
        assertEquals(2, rep.compliant());
        assertEquals(0, rep.nonCompliant());
        assertEquals(0, rep.unknown());
        assertTrue(rep.items().isEmpty());
        assertFalse(rep.policy().asksAnything());
    }

    @Test void phonesAreSortedIntoCompliantNonCompliantAndUnknown() throws Exception {
        Rec r = new Rec(); r.policy = policyRow("2.4", 12, true, true);
        r.devices.add(dev(D1, "Zulu good", G1, "Thandi", "Nkosi", "2.4", "12", true, false));
        r.devices.add(dev(D2, "Bravo old", G2, "Sipho", "Dlamini", "2.0", "12", true, false));
        r.devices.add(dev(D3, "Alpha silent", null, null, null, null, null, null, null));
        r.devices.add(dev(D4, "Charlie rooted", null, null, null, "2.5", "13", true, true));
        var rep = svc(r).report(tid());
        assertEquals(4, rep.activeDevices());
        assertEquals(1, rep.compliant());
        assertEquals(2, rep.nonCompliant());
        assertEquals(1, rep.unknown());
        assertEquals(List.of("Bravo old", "Charlie rooted", "Alpha silent"), rep.items().stream().map(DeviceComplianceService.Item::deviceName).toList(),
                "non-compliant first, then unknown, each by name; compliant phones are not listed");
        var bravo = rep.items().get(0);
        assertEquals("NON_COMPLIANT", bravo.status());
        assertEquals(List.of("APP_TOO_OLD"), bravo.violations());
        assertEquals("Sipho Dlamini", bravo.guardName());
        assertEquals(G2, bravo.guardId());
        assertEquals(List.of("ROOTED"), rep.items().get(1).violations());
        var alpha = rep.items().get(2);
        assertEquals("UNKNOWN", alpha.status());
        assertEquals(List.of("APP_VERSION", "OS_VERSION", "SCREEN_LOCK", "ROOT_STATUS"), alpha.missing());
        assertNull(alpha.guardName());
    }

    @Test void theReportAsksOnlyForThisCompanysActivePhones() throws Exception {
        Rec r = new Rec();
        svc(r).report(tid());
        var q = r.log.stream().filter(x -> x.sql().contains("FROM security_devices d")).findFirst().orElseThrow();
        assertTrue(q.sql().contains("d.tenant_id = ?"));
        assertTrue(q.sql().contains("d.status = 'ACTIVE'"));
        assertTrue(q.sql().contains("g.tenant_id = d.tenant_id"), "the guard join stays inside the company");
        assertArrayEquals(new Object[]{T}, q.args());
    }

    @Test void theReportCarriesThePolicyItWasJudgedBy() throws Exception {
        Rec r = new Rec(); r.policy = policyRow("2.4", null, false, false);
        var rep = svc(r).report(tid());
        assertEquals("2.4", rep.policy().minAppVersion());
        assertTrue(rep.policy().asksAnything());
        assertNotNull(rep.generatedAt());
    }

    @Test void theReportWritesNothing() throws Exception {
        Rec r = new Rec(); r.policy = policyRow("2.4", 12, true, true);
        r.devices.add(dev(D1, "A", null, null, null, "1.0", "1", false, true));
        svc(r).report(tid());
        assertTrue(r.writes().isEmpty());
    }

    // ---- wiring (source scans) ----

    @Test void everyStatementInTheServiceIsScopedToTheCompany() throws Exception {
        String src = read("application/internal/DeviceComplianceService.java");
        var m = java.util.regex.Pattern.compile("jdbc\\.(update|query)\\(\\s*(\"\"\"[\\s\\S]*?\"\"\"|\"[^\"]*\")").matcher(src);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(m.group(2).contains("tenant_id"), "statement without tenant_id: " + m.group(2));
        }
        assertEquals(7, n, "policy, updatedAt, upsert, audit, device lookup, posture update, report");
    }

    @Test void theServiceNeverBlocksOrChangesDeviceStatus() throws Exception {
        String src = read("application/internal/DeviceComplianceService.java");
        assertFalse(src.contains("SET status"));
        assertFalse(src.contains("DELETE FROM"));
        assertFalse(src.contains("TenantContext"), "the tenant comes from the caller");
    }

    @Test void theControllerKeepsPolicyAndReportToWholeCompanyUsers() throws Exception {
        String src = read("api/DeviceComplianceController.java");
        assertEquals(4L, java.util.regex.Pattern.compile("@(Get|Put|Post)Mapping").matcher(src).results().count());
        assertTrue(src.contains("@PostMapping(\"/api/v1/guard/me/device-posture\")"));
        assertTrue(src.contains("hasAuthority('SECURITY_MANAGE')"));
        assertTrue(src.contains("hasAuthority('SECURITY_GUARD')"));
        assertEquals(1L, java.util.regex.Pattern.compile("SiteScopeService\\.checkUnrestricted\\(").matcher(src).results().count());
        assertEquals(3L, java.util.regex.Pattern.compile("wholeCompany\\(\\)\\)|wholeCompany\\(\\);").matcher(src).results().count(), "policy read, policy save, report");
        int posture = src.indexOf("public ResponseEntity<ApiResponse<Void>> posture");
        assertTrue(posture > 0);
        assertFalse(src.substring(posture).contains("wholeCompany"), "a guard's own phone report is not site-scoped");
        assertEquals(4L, java.util.regex.Pattern.compile("featureGuard\\.requireModule\\(\"security\"\\)").matcher(src).results().count());
    }

    @Test void theMigrationAndTheServiceAgreeOnColumnNames() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V870__device_compliance.sql"));
        String src = read("application/internal/DeviceComplianceService.java");
        for (String col : List.of("min_app_version", "min_os_major", "require_screen_lock", "block_rooted", "os_version", "screen_lock_on", "rooted", "posture_reported_at"))
            assertTrue(sql.contains(col) && src.contains(col), col);
    }
}
