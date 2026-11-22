package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FleetAlertRulesTest {
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static Instant ago(long hours) { return NOW.minusSeconds(hours * 3600); }

    @Test void newUrgentProblemAlertsOnce() {
        var d = FleetAlertRules.decide(List.of("OFFLINE"), Set.of(), null, NOW);
        assertEquals(Set.of("OFFLINE"), d.alertNow());
        assertEquals(Set.of("OFFLINE"), d.newAlerted());
        assertTrue(d.changed());
    }
    @Test void sameProblemIsNotAlertedAgainSoon() {
        var d = FleetAlertRules.decide(List.of("OFFLINE"), Set.of("OFFLINE"), ago(1), NOW);
        assertTrue(d.alertNow().isEmpty());
        assertEquals(Set.of("OFFLINE"), d.newAlerted());
        assertFalse(d.changed());
    }
    @Test void reminderAfterSixHours() {
        assertTrue(FleetAlertRules.decide(List.of("OFFLINE"), Set.of("OFFLINE"), ago(5), NOW).alertNow().isEmpty());
        assertEquals(Set.of("OFFLINE"), FleetAlertRules.decide(List.of("OFFLINE"), Set.of("OFFLINE"), ago(6), NOW).alertNow());
    }
    @Test void clearedProblemIsForgottenSoItAlertsAgainWhenItReturns() {
        var cleared = FleetAlertRules.decide(List.of(), Set.of("OFFLINE"), ago(1), NOW);
        assertTrue(cleared.alertNow().isEmpty());
        assertTrue(cleared.newAlerted().isEmpty());
        assertTrue(cleared.changed());
        assertEquals(Set.of("OFFLINE"), FleetAlertRules.decide(List.of("OFFLINE"), Set.of(), null, NOW).alertNow());
    }
    @Test void aSecondProblemAlertsWithoutRepeatingTheFirst() {
        var d = FleetAlertRules.decide(List.of("OFFLINE", "CRITICAL_BATTERY"), Set.of("OFFLINE"), ago(1), NOW);
        assertEquals(Set.of("CRITICAL_BATTERY"), d.alertNow());
        assertEquals(Set.of("CRITICAL_BATTERY", "OFFLINE"), d.newAlerted());
    }
    @Test void onlyUrgentProblemsAlert() {
        var d = FleetAlertRules.decide(List.of("LOW_BATTERY", "OLD_APP", "NEVER_SEEN"), Set.of(), null, NOW);
        assertTrue(d.alertNow().isEmpty());
        assertFalse(d.changed());
    }
    @Test void storedFormRoundTrips() {
        assertEquals("CRITICAL_BATTERY,OFFLINE", FleetAlertRules.format(Set.of("OFFLINE", "CRITICAL_BATTERY")));
        assertNull(FleetAlertRules.format(Set.of()));
        assertEquals(Set.of("OFFLINE", "CRITICAL_BATTERY"), FleetAlertRules.parse("CRITICAL_BATTERY, OFFLINE"));
        assertTrue(FleetAlertRules.parse(null).isEmpty());
        assertTrue(FleetAlertRules.parse("  ").isEmpty());
    }
    @Test void digestWording() {
        var one = FleetAlertRules.digest(List.of(new FleetAlertRules.Line("Gate phone", "Thabo M", Set.of("OFFLINE", "CRITICAL_BATTERY"))));
        assertEquals("1 phone needs attention", one.title());
        assertTrue(one.message().contains("Gate phone (Thabo M): "));
        assertTrue(one.message().contains("silent while on shift") && one.message().contains("battery almost flat"));
        var many = new java.util.ArrayList<FleetAlertRules.Line>();
        for (int i = 0; i < 7; i++) many.add(new FleetAlertRules.Line("P" + i, null, Set.of("OFFLINE")));
        var t = FleetAlertRules.digest(many);
        assertEquals("7 phones need attention", t.title());
        assertTrue(t.message().contains("P4") && !t.message().contains("P5"));
        assertTrue(t.message().endsWith("and 2 more"));
        assertTrue(FleetAlertRules.digest(List.of(new FleetAlertRules.Line(null, null, Set.of("OFFLINE")))).message().startsWith("Unnamed phone: "));
    }

    // ---- fix to chunk 38: a fresh clock-in is not "silent" ----
    @Test void sessionStartCountsAsHeardFrom() {
        Instant start = ago(0), old = ago(5);
        assertEquals(start, FleetHealthRules.effectiveLastSeen(old, start));
        assertEquals(old, FleetHealthRules.effectiveLastSeen(old, ago(9)));
        assertEquals(start, FleetHealthRules.effectiveLastSeen(null, start));
        assertEquals(old, FleetHealthRules.effectiveLastSeen(old, null));
        assertNull(FleetHealthRules.effectiveLastSeen(null, null));
    }

    // ---- wiring ----
    @Test void schedulerIsOffByDefaultAndLeased() throws Exception {
        String s = read("application/internal/FleetAlertScheduler.java");
        assertTrue(s.contains("handyflow.security.fleet.alerts-enabled:false"));
        assertTrue(s.contains("if (!enabled) return;"));
        assertTrue(s.contains("SecurityJobRunner.exclusively(jobs, \"security-fleet-alerts\""));
        assertTrue(s.contains("NotificationChannel.IN_APP, NotificationChannel.EMAIL"));
        assertFalse(s.contains("NotificationChannel.SMS"), "digest goes to in-app and e-mail only");
    }
    @Test void stateIsSavedBeforeTheMessageIsSent() throws Exception {
        String s = read("application/internal/FleetAlertScheduler.java");
        int m = s.indexOf("int forTenant(");
        int save = s.indexOf("deviceRepository.save(d);          // saved before sending", m);
        int send = s.indexOf("send(tenantId, FleetAlertRules.digest(lines));", m);
        assertTrue(m > 0 && save > m && send > save);
    }
    @Test void healthServiceUsesTheSessionStart() throws Exception {
        String s = read("application/internal/FleetHealthService.java");
        assertTrue(s.contains("FleetHealthRules.effectiveLastSeen(d.getLastSeenAt(), sessionStart.get(d.getId()))"));
        assertTrue(s.contains("g.getTenantId().equals(tenantId)") || read("application/internal/FleetAlertScheduler.java").contains("g.getTenantId().equals(tenantId)"));
    }
    @Test void migrationAndEntityAgree() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V852__security_device_fleet_alert_state.sql"));
        assertTrue(m.contains("fleet_alerted_issues VARCHAR(100)") && m.contains("fleet_alerted_at TIMESTAMPTZ"));
        String e = read("domain/model/SecurityDevice.java");
        assertTrue(e.contains("@Column(name = \"fleet_alerted_issues\", length = 100)") && e.contains("@Column(name = \"fleet_alerted_at\")"));
    }
}
