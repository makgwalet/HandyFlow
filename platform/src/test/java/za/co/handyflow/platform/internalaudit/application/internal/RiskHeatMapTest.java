package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.RiskHeatMap.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RiskHeatMapTest {
    UUID payroll = UUID.randomUUID(), treasury = UUID.randomUUID(), stores = UUID.randomUUID(), fleet = UUID.randomUUID(), it = UUID.randomUUID();
    List<AreaIn> areas = List.of(new AreaIn(payroll, "Payroll", "Finance"), new AreaIn(treasury, "Treasury", "finance"), new AreaIn(stores, "Stores", "Operations"),
            new AreaIn(fleet, "Fleet", null), new AreaIn(it, "Access control", "IT"));

    private Cell cell(HeatMap m, int inherent, int control) { return m.cells().stream().filter(c -> c.inherent() == inherent && c.control() == control).findFirst().get(); }

    @Test
    void theGridHasTwentyFiveSquaresHighestInherentRiskFirst() {
        HeatMap m = RiskHeatMap.build(areas, List.of());
        assertEquals(25, m.cells().size());
        assertEquals(5, m.cells().get(0).inherent());
        assertEquals(1, m.cells().get(0).control());
        assertEquals(1, m.cells().get(24).inherent());
        assertEquals(5, m.cells().get(24).control());
    }

    @Test
    void anAreaLandsInTheSquareOfItsInherentAndControlScores() {
        HeatMap m = RiskHeatMap.build(areas, List.of(new AssessmentIn(payroll, 5, 4, "CRITICAL"), new AssessmentIn(treasury, 5, 4, "HIGH"), new AssessmentIn(stores, 2, 1, "LOW")));
        assertEquals(2, cell(m, 5, 4).count());
        assertEquals(List.of("Payroll", "Treasury"), cell(m, 5, 4).areas());
        assertEquals(1, cell(m, 2, 1).count());
        assertEquals(0, cell(m, 3, 3).count());
        assertEquals(3, m.assessed());
        assertEquals(2, m.notAssessed());
        assertEquals(5, m.total());
    }

    @Test
    void processAreasAreGroupedWithoutRegardToCaseAndCountedByFinalRisk() {
        HeatMap m = RiskHeatMap.build(areas, List.of(new AssessmentIn(payroll, 5, 4, "CRITICAL"), new AssessmentIn(treasury, 3, 3, "high"), new AssessmentIn(stores, 2, 1, "LOW")));
        Row fin = m.byProcessArea().stream().filter(r -> r.processArea().equalsIgnoreCase("Finance")).findFirst().get();
        assertEquals(2, fin.total());
        assertEquals(1, fin.critical());
        assertEquals(1, fin.high());          // "high" in lower case still counts
        assertEquals(0, fin.notAssessed());
        assertEquals(4, m.byProcessArea().size());   // Finance, Operations, IT, and the one with no process area
    }

    @Test
    void anAreaWithNoAssessmentOrAnUnknownLevelIsNotAssessed() {
        HeatMap m = RiskHeatMap.build(areas, List.of(new AssessmentIn(it, 4, 4, "SEVERE")));
        Row r = m.byProcessArea().stream().filter(x -> x.processArea().equals("IT")).findFirst().get();
        assertEquals(1, r.notAssessed());
        assertEquals(1, cell(m, 4, 4).count());    // it is still placed on the grid by its scores
        assertEquals(RiskHeatMap.NO_AREA, m.byProcessArea().stream().filter(x -> x.processArea().equals(RiskHeatMap.NO_AREA)).findFirst().get().processArea());
    }

    @Test
    void theWorstProcessAreasComeFirst() {
        HeatMap m = RiskHeatMap.build(areas, List.of(new AssessmentIn(stores, 5, 5, "CRITICAL"), new AssessmentIn(payroll, 4, 4, "HIGH"), new AssessmentIn(treasury, 4, 4, "HIGH")));
        assertEquals("Operations", m.byProcessArea().get(0).processArea());     // one critical outranks two high
        assertEquals("Finance", m.byProcessArea().get(1).processArea());      // the first spelling met is kept
    }

    @Test
    void scoresOutsideOneToFiveAreHeldAtTheEdges() {
        HeatMap m = RiskHeatMap.build(areas, List.of(new AssessmentIn(payroll, 9, -2, "HIGH")));
        assertEquals(1, cell(m, 5, 1).count());
    }

    @Test
    void anEmptyUniverseGivesAnEmptyMap() {
        HeatMap m = RiskHeatMap.build(List.of(), List.of());
        assertEquals(25, m.cells().size());
        assertTrue(m.byProcessArea().isEmpty());
        assertEquals(0, m.total());
    }

    @Test void assessedAreasComeWithTheirClampedScoresAndUnassessedOnesDoNot() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        HeatMap m = RiskHeatMap.build(
                List.of(new AreaIn(a, "Payroll", "HR"), new AreaIn(b, "Stores", null), new AreaIn(c, "Fleet", "Ops")),
                List.of(new AssessmentIn(a, 9, 0, "HIGH"), new AssessmentIn(b, 2, 3, "LOW")));
        assertEquals(2, m.areas().size());
        assertEquals("Payroll", m.areas().get(0).name());
        assertEquals(5, m.areas().get(0).inherent());
        assertEquals(1, m.areas().get(0).control());
        assertEquals("No process area", m.areas().get(1).processArea());
        assertEquals("LOW", m.areas().get(1).finalRisk());
    }
}
