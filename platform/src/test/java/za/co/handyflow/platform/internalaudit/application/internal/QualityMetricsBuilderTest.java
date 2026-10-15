package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.QualityMetricsBuilder.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QualityMetricsBuilderTest {
    UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID(), e3 = UUID.randomUUID();
    Instant start = Instant.parse("2026-10-01T00:00:00Z");

    ReviewIn done(UUID e, String conclusion, int days, String... failed) {
        return new ReviewIn(e, "COMPLETED", conclusion, start, start.plusSeconds(days * 86400L), List.of(failed));
    }

    @Test
    void conclusionsAverageTimeAndMostFailedChecks() {
        var m = QualityMetricsBuilder.build(List.of(done(e1, "SATISFACTORY", 2), done(e2, "NEEDS_IMPROVEMENT", 4, "F4", "R4"), done(e3, "UNSATISFACTORY", 6, "F4", "F5"),
                new ReviewIn(e3, "IN_PROGRESS", null, start, null, List.of()), new ReviewIn(e1, "CANCELLED", null, start, null, List.of())), Set.of(), List.of(), List.of());
        assertEquals(3, m.reviewsCompleted());
        assertEquals(1, m.reviewsLive());
        assertEquals(33, m.satisfactoryPct());
        assertEquals(4.0, m.averageDaysToComplete());
        assertEquals("F4", m.mostFailedChecks().get(0).code());
        assertEquals(2, m.mostFailedChecks().get(0).count());
        assertEquals("Have all workpapers been reviewed and signed off?", m.mostFailedChecks().get(0).question());
        assertEquals(List.of("SATISFACTORY", "NEEDS_IMPROVEMENT", "UNSATISFACTORY"), m.byConclusion().stream().map(ConclusionRow::conclusion).toList());
    }

    @Test
    void issuedReportsWithoutACompletedReviewAreFlagged() {
        var m = QualityMetricsBuilder.build(List.of(done(e1, "SATISFACTORY", 1), new ReviewIn(e2, "IN_PROGRESS", null, start, null, List.of())),
                Set.of(e1, e2, e3), List.of(), List.of());
        assertEquals(2, m.issuedWithoutCompletedReview());
        assertTrue(m.issuedWithoutCompletedReviewEngagements().containsAll(List.of(e2, e3)));
    }

    @Test
    void reviewNotesAndReopenedFindingsPercentages() {
        var m = QualityMetricsBuilder.build(List.of(), Set.of(), List.of(new NoteIn("CLEARED"), new NoteIn("OPEN"), new NoteIn("RESPONDED"), new NoteIn("CLEARED")),
                List.of(new FindingIn(true), new FindingIn(false), new FindingIn(false), new FindingIn(false)));
        assertEquals(4, m.reviewNotesRaised());
        assertEquals(50, m.reviewNotesClearedPct());
        assertEquals(25, m.findingsReopenedPct());
    }

    @Test
    void emptyIsZerosAndNoAverage() {
        var m = QualityMetricsBuilder.build(List.of(), Set.of(), List.of(), List.of());
        assertEquals(0, m.satisfactoryPct());
        assertNull(m.averageDaysToComplete());
        assertEquals(0, m.reviewNotesClearedPct());
        assertTrue(m.mostFailedChecks().isEmpty());
    }

    @Test
    void checklistHasUniqueCodesAndLookupIsCaseInsensitive() {
        assertEquals(QualityChecklist.items().size(), QualityChecklist.items().stream().map(QualityChecklist.Item::code).distinct().count());
        assertTrue(QualityChecklist.find("f4").isPresent());
        assertTrue(QualityChecklist.find("Z9").isEmpty());
    }
}
