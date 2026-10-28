package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardTrendsTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    static final Metrics NOW = new Metrics(12, 18, 7, 5);

    private static Snapshot snap(int daysAgo, int e, int f, int o, int h) { return new Snapshot(TODAY.minusDays(daysAgo), new Metrics(e, f, o, h)); }

    @Test
    void withNoHistoryThereIsNothingToCompare() {
        Trends t = DashboardTrends.compare(NOW, List.of(), TODAY);
        assertNull(t.since());
        assertNull(t.daysAgo());
        assertTrue(t.changes().isEmpty());
    }

    @Test
    void aSavedDayLessThanAWeekOldIsTooRecent() {
        assertTrue(DashboardTrends.compare(NOW, List.of(snap(6, 1, 1, 1, 1), snap(1, 1, 1, 1, 1)), TODAY).changes().isEmpty());
        Trends t = DashboardTrends.compare(NOW, List.of(snap(7, 1, 1, 1, 1)), TODAY);
        assertEquals(7, t.daysAgo().intValue());
        assertEquals(DashboardTrends.SAVED, t.basis());
    }

    @Test
    void itComparesWithTheDayNearestAQuarterAgo() {
        Trends t = DashboardTrends.compare(NOW, List.of(snap(10, 11, 20, 4, 5), snap(85, 9, 15, 10, 4), snap(120, 1, 1, 1, 1)), TODAY);
        assertEquals(TODAY.minusDays(85), t.since());
        assertEquals(85, t.daysAgo().intValue());
        assertEquals(4, t.changes().size());
    }

    @Test
    void whenTwoDaysAreEquallyNearTheOlderOneWins() {
        Trends t = DashboardTrends.compare(NOW, List.of(snap(95, 1, 1, 1, 1), snap(85, 2, 2, 2, 2)), TODAY);
        assertEquals(95, t.daysAgo().intValue());
    }

    @Test
    void changesCarryNowBeforeAndTheDifferenceInEachDirection() {
        Trends t = DashboardTrends.compare(NOW, List.of(snap(90, 9, 20, 7, 5)), TODAY);
        Change eng = t.changes().get(0), open = t.changes().get(1), overdue = t.changes().get(2), high = t.changes().get(3);
        assertEquals("engagements", eng.key());
        assertEquals(3, eng.delta());
        assertEquals(9, eng.before());
        assertEquals(12, eng.now());
        assertEquals("openFindings", open.key());
        assertEquals(-2, open.delta());
        assertEquals(0, overdue.delta());
        assertEquals("highRiskAreas", high.key());
        assertEquals(0, high.delta());
    }

    @Test
    void anOldDayIsStillUsedWhenItIsAllThereIs() {
        assertEquals(400, DashboardTrends.compare(NOW, List.of(snap(400, 1, 1, 1, 1)), TODAY).daysAgo().intValue());
    }
}
