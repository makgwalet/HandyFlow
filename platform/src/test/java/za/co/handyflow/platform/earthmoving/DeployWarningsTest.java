package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.DeployWarnings;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeployWarningsTest {
    static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

    @Test void aMachineInspectedThisMorningWithNothingOverdueHasNoWarnings() {
        assertTrue(DeployWarnings.compute(List.of(), false, NOW.minusSeconds(3 * 3600), "PASS", NOW).isEmpty());
    }
    @Test void neverInspectedIsWarnedAbout() {
        assertEquals(List.of("This machine has never been inspected."), DeployWarnings.compute(List.of(), false, null, null, NOW));
    }
    @Test void anInspectionOlderThan24HoursNoLongerCounts() {
        assertEquals(List.of("No inspection in the last 24 hours."),
                DeployWarnings.compute(List.of(), false, NOW.minusSeconds(25 * 3600), "PASS", NOW));
        assertTrue(DeployWarnings.compute(List.of(), false, NOW.minusSeconds(23 * 3600), "PASS", NOW).isEmpty());
    }
    @Test void openDefectsFromTheLastInspectionAreMentioned() {
        List<String> w = DeployWarnings.compute(List.of(), false, NOW.minusSeconds(3600), "DEFECTS", NOW);
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("defects"));
    }
    @Test void eachOverduePlanIsNamed() {
        List<String> w = DeployWarnings.compute(List.of("250 hour service", "Annual roadworthy"), true, NOW, "PASS", NOW);
        assertEquals(List.of("Service overdue: 250 hour service.", "Service overdue: Annual roadworthy."), w);
    }
    @Test void aMachineAtItsIntervalWithNoPlanIsStillWarned() {
        assertEquals(List.of("This machine has reached its service interval."), DeployWarnings.compute(List.of(), true, NOW, "PASS", NOW));
    }
}
