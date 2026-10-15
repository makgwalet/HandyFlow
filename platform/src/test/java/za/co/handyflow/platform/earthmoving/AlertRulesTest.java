package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.AlertRules;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAlert;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AlertRulesTest {
    static final UUID U1 = UUID.randomUUID(), U2 = UUID.randomUUID();
    static final Instant T0 = Instant.parse("2026-10-15T08:00:00Z");

    private static EarthAlert alert() {
        return EarthAlert.create(TenantId.generate(), "BREAKDOWN", "CRITICAL", UUID.randomUUID(), "ASSET", UUID.randomUUID(),
                "Breakdown", "D9 broke down", "BREAKDOWN:x");
    }

    // ── rule limits ──────────────────────────────────────────────────────────
    @Test void aBreakdownRuleCannotBeSwitchedOff() {
        assertThrows(IllegalArgumentException.class, () -> AlertRules.checkRule("BREAKDOWN", false, null, List.of(), List.of()));
        assertDoesNotThrow(() -> AlertRules.checkRule("SERVICE_DUE", false, null, List.of(), List.of()));
    }
    @Test void aBreakdownMustEscalateWithinAnHourWhenThereIsAnEscalationLevel() {
        assertThrows(IllegalArgumentException.class, () -> AlertRules.checkRule("BREAKDOWN", true, 61, List.of(U1), List.of(U2)));
        assertDoesNotThrow(() -> AlertRules.checkRule("BREAKDOWN", true, 60, List.of(U1), List.of(U2)));
        assertDoesNotThrow(() -> AlertRules.checkRule("BREAKDOWN", true, 0, List.of(U1), List.of(U2)));
    }
    @Test void escalationRecipientsNeedAWaitingTime() {
        assertThrows(IllegalArgumentException.class, () -> AlertRules.checkRule("SERVICE_DUE", true, null, List.of(U1), List.of(U2)));
    }
    @Test void waitingTimeMustBeSensible() {
        assertThrows(IllegalArgumentException.class, () -> AlertRules.checkRule("SERVICE_DUE", true, -1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> AlertRules.checkRule("SERVICE_DUE", true, 7 * 24 * 60 + 1, List.of(), List.of()));
        assertDoesNotThrow(() -> AlertRules.checkRule("SERVICE_DUE", true, 7 * 24 * 60, List.of(U1), List.of(U2)));
    }
    @Test void unknownTypesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> AlertRules.checkRule("FIRE", true, null, List.of(), List.of()));
    }
    @Test void escalationTiming() {
        assertFalse(AlertRules.isEscalationDue(T0, T0.plusSeconds(29 * 60), 30));
        assertTrue(AlertRules.isEscalationDue(T0, T0.plusSeconds(30 * 60), 30));
        assertTrue(AlertRules.isEscalationDue(T0, T0, 0));
        assertFalse(AlertRules.isEscalationDue(T0, T0.plusSeconds(99999), null));
    }
    @Test void breakdownSeverityFollowsTheIncident() {
        assertEquals("CRITICAL", AlertRules.severityFor("BREAKDOWN", "CRITICAL"));
        assertEquals("CRITICAL", AlertRules.severityFor("BREAKDOWN", "HIGH"));
        assertEquals("WARNING", AlertRules.severityFor("BREAKDOWN", "LOW"));
        assertEquals("WARNING", AlertRules.severityFor("SERVICE_DUE", "CRITICAL"));
    }

    // ── the alert itself ─────────────────────────────────────────────────────
    @Test void anAlertStartsOpenAtLevelOne() {
        EarthAlert a = alert();
        assertEquals("OPEN", a.getStatus());
        assertEquals(1, a.getLevel());
        assertTrue(a.isOpen());
        assertTrue(a.canEscalate());
    }
    @Test void acknowledgingStopsEscalation() {
        EarthAlert a = alert();
        a.acknowledge(U1);
        assertEquals("ACKNOWLEDGED", a.getStatus());
        assertEquals(U1, a.getAcknowledgedBy());
        assertFalse(a.canEscalate());
        assertThrows(IllegalStateException.class, a::escalate);
        assertThrows(IllegalStateException.class, () -> a.acknowledge(U2));
    }
    @Test void anAlertEscalatesOnlyOnce() {
        EarthAlert a = alert();
        a.escalate();
        assertEquals(2, a.getLevel());
        assertNotNull(a.getEscalatedAt());
        assertThrows(IllegalStateException.class, a::escalate);
    }
    @Test void anEscalatedAlertCanStillBeAcknowledgedAndResolved() {
        EarthAlert a = alert();
        a.escalate();
        a.acknowledge(U1);
        a.resolve(U1, "Repaired");
        assertEquals("RESOLVED", a.getStatus());
        assertFalse(a.isOpen());
        assertThrows(IllegalStateException.class, () -> a.resolve(U1, "again"));
        assertThrows(IllegalStateException.class, () -> a.acknowledge(U1));
    }
    @Test void anOpenAlertCanBeResolvedDirectly() {
        EarthAlert a = alert();
        a.resolve(U1, "Duplicate");
        assertEquals("Duplicate", a.getResolutionNote());
    }
}
