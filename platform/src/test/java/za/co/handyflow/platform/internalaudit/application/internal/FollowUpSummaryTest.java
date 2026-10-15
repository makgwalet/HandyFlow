package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.FollowUpSummary.Line;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FollowUpSummaryTest {
    @Test
    void countsOutcomesAndPercentage() {
        var c = FollowUpSummary.count(Arrays.asList("IMPLEMENTED", "IMPLEMENTED", "PARTIALLY_IMPLEMENTED", "NOT_IMPLEMENTED"));
        assertEquals(4, c.total());
        assertEquals(2, c.implemented());
        assertEquals(1, c.partial());
        assertEquals(1, c.notImplemented());
        assertEquals(50, c.implementedPct());
        assertTrue(c.complete());
    }

    @Test
    void noLongerApplicableIsNotCountedAgainstTheBusiness() {
        var c = FollowUpSummary.count(Arrays.asList("IMPLEMENTED", "NO_LONGER_APPLICABLE", "NO_LONGER_APPLICABLE", "NOT_IMPLEMENTED"));
        assertEquals(2, c.noLongerApplicable());
        assertEquals(50, c.implementedPct());
    }

    @Test
    void unassessedMeansNotComplete() {
        var c = FollowUpSummary.count(Arrays.asList("IMPLEMENTED", null));
        assertFalse(c.complete());
        assertEquals(1, c.assessed());
    }

    @Test
    void emptyAndAllNotApplicableGiveZeroPercent() {
        assertEquals(0, FollowUpSummary.count(List.of()).implementedPct());
        assertFalse(FollowUpSummary.count(List.of()).complete());
        assertEquals(0, FollowUpSummary.count(List.of("NO_LONGER_APPLICABLE")).implementedPct());
    }

    @Test
    void reportTextListsWorstFirstWithNotes() {
        String t = FollowUpSummary.reportText(List.of(
                new Line("Minor thing", "LOW", "IMPLEMENTED", "Checked the register."),
                new Line("Big thing", "critical", "NOT_IMPLEMENTED", "No change since last year."),
                new Line("Unrated", null, null, null)));
        assertTrue(t.startsWith("1 of 3 applicable findings are implemented (33%)."));
        assertTrue(t.indexOf("Big thing") < t.indexOf("Minor thing"));
        assertTrue(t.indexOf("Minor thing") < t.indexOf("Unrated"));
        assertTrue(t.contains("[CRITICAL] — Not implemented"));
        assertTrue(t.contains("[unrated] — Not yet assessed"));
        assertTrue(t.contains("No change since last year."));
    }

    @Test
    void reportTextWithNothing() {
        assertEquals("No earlier findings were followed up.", FollowUpSummary.reportText(List.of()));
    }
}
