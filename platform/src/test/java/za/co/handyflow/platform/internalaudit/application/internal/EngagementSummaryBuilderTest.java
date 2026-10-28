package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.EngagementSummaryBuilder.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngagementSummaryBuilderTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    static final PlanningIn NONE = new PlanningIn(false, false, false, false, false);
    static final PlanningIn ALL = new PlanningIn(true, true, true, true, true);

    private Inputs in(String status, PlanningIn planning, int wp, int signed, int steps, int stepsDone, int sampled, int tested, ReportIn report) {
        return new Inputs(status, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 15), LocalDate.of(2026, 8, 20), planning, wp, signed, steps, stepsDone, sampled, tested,
                report, 4, 3, 1, 6, 2, 1, 3);
    }
    private Phase phase(Summary s, String key) { return s.phases().stream().filter(p -> p.key().equals(key)).findFirst().get(); }
    private Milestone ms(Summary s, String key) { return s.milestones().stream().filter(m -> m.key().equals(key)).findFirst().get(); }

    @Test
    void aNewEngagementIsAtNothingAndSaysWhatIsMissing() {
        Summary s = EngagementSummaryBuilder.build(in("PLANNING", NONE, 0, 0, 0, 0, 0, 0, null), TODAY);
        assertEquals(0, s.overallPct());
        assertEquals(4, s.phases().size());
        assertEquals("no workpapers yet", phase(s, "fieldwork").basis());
        assertEquals("no programme or sample yet", phase(s, "testing").basis());
        assertEquals("no report yet", phase(s, "reporting").basis());
        assertNull(s.reportStatus());
    }

    @Test
    void planningCountsTheFiveThingsThatMustBeSet() {
        assertEquals(60, phase(EngagementSummaryBuilder.build(in("PLANNING", new PlanningIn(true, true, false, true, false), 0, 0, 0, 0, 0, 0, null), TODAY), "planning").pct());
        assertEquals(100, phase(EngagementSummaryBuilder.build(in("PLANNING", ALL, 0, 0, 0, 0, 0, 0, null), TODAY), "planning").pct());
    }

    @Test
    void fieldworkIsWorkpapersSignedOff() {
        Phase f = phase(EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 8, 6, 0, 0, 0, 0, null), TODAY), "fieldwork");
        assertEquals(75, f.pct());
        assertEquals(6, f.done());
        assertEquals(8, f.total());
    }

    @Test
    void testingUsesTheProgrammeWhenThereIsOneAndOtherwiseTheSample() {
        assertEquals("programme steps done", phase(EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 0, 0, 10, 4, 20, 20, null), TODAY), "testing").basis());
        assertEquals(40, phase(EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 0, 0, 10, 4, 20, 20, null), TODAY), "testing").pct());
        Phase sample = phase(EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 0, 0, 0, 0, 20, 5, null), TODAY), "testing");
        assertEquals("sampled items tested", sample.basis());
        assertEquals(25, sample.pct());
    }

    @Test
    void reportingFollowsTheReportsStage() {
        int[] want = {25, 50, 75, 100, 100};
        String[] st = {"DRAFT", "FACTUAL_REVIEW", "READY", "ISSUED", "SUPERSEDED"};
        for (int i = 0; i < st.length; i++)
            assertEquals(want[i], phase(EngagementSummaryBuilder.build(in("REPORTING", ALL, 0, 0, 0, 0, 0, 0, new ReportIn(st[i], TODAY, null)), TODAY), "reporting").pct(), st[i]);
    }

    @Test
    void overallIsTheAverageOfTheFourPhases() {
        Summary s = EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 4, 2, 10, 5, 0, 0, new ReportIn("DRAFT", TODAY, null)), TODAY);
        assertEquals(56, s.overallPct());       // planning 100, fieldwork 50, testing 50, reporting 25: 225 / 4 = 56.25
    }

    @Test
    void milestonesRunInOrderWithOneCurrentAndTheRestPending() {
        Summary s = EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 4, 2, 0, 0, 0, 0, null), TODAY);
        assertEquals("DONE", ms(s, "created").status());
        assertEquals("DONE", ms(s, "planning").status());
        assertEquals("CURRENT", ms(s, "fieldwork").status());
        assertEquals("PENDING", ms(s, "testing").status());
        assertEquals("PENDING", ms(s, "issued").status());
        assertEquals(1, s.milestones().stream().filter(m -> m.status().equals("CURRENT")).count());
        assertEquals(LocalDate.of(2026, 8, 20), ms(s, "created").date());
        assertEquals("PENDING", ms(s, "end").status());
    }

    @Test
    void anIssuedReportCompletesTheLastMilestonesAndAClosedEngagementCompletesAll() {
        Summary issued = EngagementSummaryBuilder.build(in("REPORTING", ALL, 4, 4, 10, 10, 0, 0, new ReportIn("ISSUED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20))), TODAY);
        assertEquals("DONE", ms(issued, "issued").status());
        assertEquals(LocalDate.of(2026, 10, 20), ms(issued, "issued").date());
        Summary closed = EngagementSummaryBuilder.build(in("CLOSED", NONE, 0, 0, 0, 0, 0, 0, null), TODAY);
        assertTrue(closed.milestones().stream().allMatch(m -> m.status().equals("DONE")));
    }

    @Test
    void theEndDateIsLateWhenPastAndTheEngagementIsStillOpen() {
        Inputs late = new Inputs("FIELDWORK", null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 8, 1), ALL, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 1);
        assertEquals("LATE", ms(EngagementSummaryBuilder.build(late, TODAY), "end").status());
        Inputs none = new Inputs("FIELDWORK", null, null, LocalDate.of(2026, 8, 1), ALL, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 1);
        assertTrue(EngagementSummaryBuilder.build(none, TODAY).milestones().stream().noneMatch(m -> m.key().equals("end")));
    }

    @Test
    void countsAreCarriedThrough() {
        Counts c = EngagementSummaryBuilder.build(in("FIELDWORK", ALL, 8, 6, 0, 0, 0, 0, null), TODAY).counts();
        assertEquals(8, c.workpapers());
        assertEquals(6, c.workpapersSignedOff());
        assertEquals(4, c.findings());
        assertEquals(3, c.openFindings());
        assertEquals(1, c.findingsPastDue());
        assertEquals(6, c.actions());
        assertEquals(2, c.actionsValidated());
        assertEquals(1, c.actionsOverdue());
        assertEquals(3, c.team());
    }
}
