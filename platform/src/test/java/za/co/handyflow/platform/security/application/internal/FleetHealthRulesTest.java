package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.FleetHealthRules.Issue.*;

import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FleetHealthRulesTest {
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static Instant ago(long minutes) { return NOW.minusSeconds(minutes * 60); }
    private static final FleetHealthRules.Config CFG = FleetHealthRules.Config.defaults();
    private static List<FleetHealthRules.Issue> a(boolean active, Instant seen, Instant created, Integer bat, String ver, boolean on, FleetHealthRules.Config c) {
        return FleetHealthRules.assess(new FleetHealthRules.Facts(active, seen, created, bat, ver, on), c, NOW);
    }

    @Test void healthyPhoneHasNoIssues() {
        assertTrue(a(true, ago(2), ago(5000), 80, "2.0.0", true, CFG).isEmpty());
    }
    @Test void silentOnShiftIsOffline() {
        assertEquals(List.of(OFFLINE), a(true, ago(45), ago(5000), 80, null, true, CFG));
        assertEquals(List.of(OFFLINE), a(true, null, ago(5000), null, null, true, CFG), "on shift and never heard from");
        assertTrue(a(true, ago(29), ago(5000), 80, null, true, CFG).isEmpty());
    }
    @Test void silentOffShiftIsNotAnAlarm() {
        assertTrue(a(true, ago(600), ago(5000), 80, null, false, CFG).isEmpty());
    }
    @Test void neverSeenOnlyAfterADay() {
        assertEquals(List.of(NEVER_SEEN), a(true, null, ago(60 * 25), null, null, false, CFG));
        assertTrue(a(true, null, ago(60 * 5), null, null, false, CFG).isEmpty());
    }
    @Test void batteryLevels() {
        assertEquals(List.of(LOW_BATTERY), a(true, ago(5), ago(5000), 20, null, false, CFG));
        assertEquals(List.of(CRITICAL_BATTERY), a(true, ago(5), ago(5000), 10, null, false, CFG));
        assertTrue(a(true, ago(5), ago(5000), 21, null, false, CFG).isEmpty());
    }
    @Test void staleBatteryReadingIsNotTrusted() {
        assertTrue(a(true, ago(180), ago(5000), 5, null, false, CFG).isEmpty(), "3 hours old: not reported as battery");
        assertEquals(List.of(OFFLINE), a(true, ago(180), ago(5000), 5, null, true, CFG), "but on shift it is offline");
    }
    @Test void outOfServicePhonesAreIgnored() {
        assertTrue(a(false, ago(500), ago(5000), 1, "1.0", true, CFG).isEmpty());
    }
    @Test void oldAppOnlyWhenAMinimumIsSet() {
        var c = new FleetHealthRules.Config(30, 24, 20, 10, "2.4.0");
        assertEquals(List.of(OLD_APP), a(true, ago(2), ago(5000), 80, "2.3.9", false, c));
        assertEquals(List.of(OLD_APP), a(true, ago(2), ago(5000), 80, null, false, c), "no version reported counts as old");
        assertTrue(a(true, ago(2), ago(5000), 80, "2.4.0", false, c).isEmpty());
        assertTrue(a(true, ago(2), ago(5000), 80, "2.10.0", false, c).isEmpty(), "2.10 is newer than 2.4");
        assertTrue(a(true, ago(2), ago(5000), 80, "1.0.0", false, CFG).isEmpty(), "no minimum, no check");
    }
    @Test void versionComparison() {
        assertTrue(FleetHealthRules.compareVersions("2.10.0", "2.9.9") > 0);
        assertTrue(FleetHealthRules.compareVersions("v2.1", "2.1.0") == 0);
        assertTrue(FleetHealthRules.compareVersions("2.1.0-beta", "2.1.0") == 0);
        assertTrue(FleetHealthRules.compareVersions("abc", "1.0") < 0);
        assertTrue(FleetHealthRules.compareVersions(null, "1.0") < 0);
    }
    @Test void criticalMeansOfflineOrCriticalBattery() {
        assertTrue(FleetHealthRules.critical(OFFLINE));
        assertTrue(FleetHealthRules.critical(CRITICAL_BATTERY));
        assertFalse(FleetHealthRules.critical(LOW_BATTERY));
        assertFalse(FleetHealthRules.critical(OLD_APP));
        assertFalse(FleetHealthRules.critical(NEVER_SEEN));
    }

    // ---- heartbeat rules ----
    @Test void heartbeatAccess() {
        UUID g = UUID.randomUUID(), other = UUID.randomUUID();
        assertEquals(HeartbeatRules.Access.OK, HeartbeatRules.access(g, true, g));
        assertEquals(HeartbeatRules.Access.OK, HeartbeatRules.access(null, true, g), "shared phone");
        assertEquals(HeartbeatRules.Access.NOT_YOURS, HeartbeatRules.access(other, true, g));
        assertEquals(HeartbeatRules.Access.NOT_YOURS, HeartbeatRules.access(other, false, g), "another guard's phone never reveals its state");
        assertEquals(HeartbeatRules.Access.INACTIVE, HeartbeatRules.access(g, false, g));
    }
    @Test void heartbeatWritesAreThrottledButChangesGetThrough() {
        assertTrue(HeartbeatRules.shouldWrite(null, NOW, null, 50, null, null));
        assertFalse(HeartbeatRules.shouldWrite(ago(0), NOW, 50, 50, "1.0", "1.0"));
        assertTrue(HeartbeatRules.shouldWrite(ago(2), NOW, 50, 50, "1.0", "1.0"));
        assertTrue(HeartbeatRules.shouldWrite(ago(0), NOW, 50, 49, "1.0", "1.0"));
        assertTrue(HeartbeatRules.shouldWrite(ago(0), NOW, 50, 50, "1.0", "1.1"));
        assertFalse(HeartbeatRules.shouldWrite(ago(0), NOW, 50, null, "1.0", null));
    }
    @Test void inputsAreCleaned() {
        assertEquals(100, HeartbeatRules.cleanBattery(250));
        assertEquals(0, HeartbeatRules.cleanBattery(-5));
        assertNull(HeartbeatRules.cleanBattery(null));
        assertNull(HeartbeatRules.cleanVersion("  "));
        assertEquals(30, HeartbeatRules.cleanVersion("x".repeat(80)).length());
    }

    // ---- wiring ----
    @Test void pingFeedsTheHeartbeatAfterOwnershipIsChecked() throws Exception {
        String s = read("api/GuardSessionController.java");
        int own = s.indexOf("sessionService.assertSessionOwnedBy(tenantId, sessionId, TenantContext.getCurrentUserId());\n        guardLocationService.recordPing");
        int hb = s.indexOf("heartbeat.reportForSession(");
        assertTrue(own > 0 && hb > own);
    }
    @Test void deviceOfAnotherGuardAnswersNotFound() throws Exception {
        String s = read("application/internal/DeviceHeartbeatService.java");
        assertTrue(s.contains("case NOT_YOURS -> throw new ResourceNotFoundException"));
        assertTrue(s.contains("case INACTIVE -> throw"));
        assertTrue(s.contains("s.getGuardId().equals(guardId)"), "a ping only counts for the session's own guard");
    }
    @Test void fleetEndpointNeedsReadPermissionAndHeartbeatNeedsGuard() throws Exception {
        String c = read("api/FleetHealthController.java");
        assertTrue(c.contains("@GetMapping(\"/api/v1/security/devices/fleet-health\")"));
        assertTrue(c.contains("hasAuthority('SECURITY_READ')"));
        assertTrue(c.contains("@PostMapping(\"/api/v1/guard/me/heartbeat\")"));
        assertTrue(c.contains("hasAuthority('SECURITY_GUARD')"));
    }
    @Test void fleetServiceFiltersGuardNamesByTenant() throws Exception {
        String s = read("application/internal/FleetHealthService.java");
        assertTrue(s.contains("g.getTenantId().equals(tenantId)"));
        assertTrue(s.contains("sessions.findOpenByTenant(tenantId)"));
    }
    @Test void migrationAndEntityAgree() throws Exception {
        assertTrue(Files.readString(Path.of("src/main/resources/db/migration/V851__security_device_app_version.sql")).contains("app_version VARCHAR(30)"));
        assertTrue(read("domain/model/SecurityDevice.java").contains("@Column(name = \"app_version\", length = 30)"));
        assertTrue(read("dto/RecordLocationPingRequest.java").contains("BigDecimal accuracyMetres) {"), "3-argument constructor kept");
    }
}
