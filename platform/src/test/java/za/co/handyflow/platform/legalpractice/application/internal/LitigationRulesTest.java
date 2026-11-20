package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LitigationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Test
    void aCaseNeedsACourtAndASideAndACaseNumberOnceIssued() {
        assertNull(LitigationRules.caseProblem("HIGH_COURT", "Gauteng Division", "PLAINTIFF", null, "PRE_ACTION", null, null, TODAY));
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "Gauteng Division", "PLAINTIFF", null, "PLEADINGS", null, null, TODAY));
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "Gauteng Division", "PLAINTIFF", " ", "TRIAL", null, null, TODAY));
        assertNull(LitigationRules.caseProblem("HIGH_COURT", "Gauteng Division", "PLAINTIFF", "12345/2026", "PLEADINGS", null, null, TODAY));
        assertNotNull(LitigationRules.caseProblem("MOON", "Gauteng Division", "PLAINTIFF", "1/26", "PLEADINGS", null, null, TODAY));
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "G", "PLAINTIFF", "1/26", "PLEADINGS", null, null, TODAY));
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "Gauteng Division", "NOBODY", "1/26", "PLEADINGS", null, null, TODAY));
    }

    @Test
    void issueAndServiceDatesMustMakeSense() {
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "Court", "PLAINTIFF", "1/26", "PLEADINGS", TODAY.plusDays(1), null, TODAY));
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "Court", "PLAINTIFF", "1/26", "PLEADINGS", null, TODAY.plusDays(1), TODAY));
        assertNotNull(LitigationRules.caseProblem("HIGH_COURT", "Court", "PLAINTIFF", "1/26", "PLEADINGS", TODAY.minusDays(1), TODAY.minusDays(5), TODAY));
        assertNull(LitigationRules.caseProblem("HIGH_COURT", "Court", "PLAINTIFF", "1/26", "PLEADINGS", TODAY.minusDays(5), TODAY.minusDays(5), TODAY));
    }

    @Test
    void movingForwardIsFreeButMovingBackNeedsAReason() {
        assertNull(LitigationRules.stageProblem("PLEADINGS", "DISCOVERY", null));
        assertNull(LitigationRules.stageProblem("PLEADINGS", "TRIAL", null));
        assertNotNull(LitigationRules.stageProblem("TRIAL", "PLEADINGS", null));
        assertNotNull(LitigationRules.stageProblem("TRIAL", "PLEADINGS", "short"));
        assertNull(LitigationRules.stageProblem("TRIAL", "PLEADINGS", "Amended pleadings were filed"));
    }

    @Test
    void stageMovesCannotConcludeReopenOrStandStill() {
        assertNotNull(LitigationRules.stageProblem("TRIAL", "CONCLUDED", "Because we are done here"));
        assertNotNull(LitigationRules.stageProblem("CONCLUDED", "TRIAL", "Because we are back"));
        assertNotNull(LitigationRules.stageProblem("TRIAL", "TRIAL", null));
        assertNotNull(LitigationRules.stageProblem("TRIAL", "NOWHERE", null));
        assertNotNull(LitigationRules.stageProblem("TRIAL", null, null));
    }

    @Test
    void concludingNeedsAnOutcomeADateAndANote() {
        LocalDate issued = TODAY.minusDays(90);
        assertNull(LitigationRules.concludeProblem("WON", TODAY, "Judgment for the plaintiff", issued, TODAY));
        assertNotNull(LitigationRules.concludeProblem("DRAW", TODAY, "Judgment for the plaintiff", issued, TODAY));
        assertNotNull(LitigationRules.concludeProblem("WON", null, "Judgment for the plaintiff", issued, TODAY));
        assertNotNull(LitigationRules.concludeProblem("WON", TODAY.plusDays(1), "Judgment for the plaintiff", issued, TODAY));
        assertNotNull(LitigationRules.concludeProblem("WON", issued.minusDays(1), "Judgment for the plaintiff", issued, TODAY));
        assertNotNull(LitigationRules.concludeProblem("WON", TODAY, "won", issued, TODAY));
        assertNull(LitigationRules.concludeProblem("SETTLED", TODAY, "Settled out of court on terms", null, TODAY));
    }

    @Test
    void weekendsAndPublicHolidaysAreWarnedAboutNotRefused() {
        assertTrue(LitigationRules.dateWarnings(LocalDate.of(2026, 11, 21)).get(0).contains("Saturday"));
        assertTrue(LitigationRules.dateWarnings(LocalDate.of(2026, 11, 22)).get(0).contains("Sunday"));
        assertTrue(LitigationRules.dateWarnings(LocalDate.of(2026, 12, 25)).stream().anyMatch(w -> w.contains("public holiday")));
        assertTrue(LitigationRules.dateWarnings(LocalDate.of(2026, 11, 20)).isEmpty());
        assertTrue(LitigationRules.dateWarnings(null).isEmpty());
    }

    @Test
    void aHearingCannotBeHeldBeforeItHappensAndNeedsAnOutcome() {
        assertNotNull(LitigationRules.heldProblem(TODAY.plusDays(1), "Matter was postponed sine die", TODAY));
        assertNotNull(LitigationRules.heldProblem(TODAY, "short", TODAY));
        assertNull(LitigationRules.heldProblem(TODAY, "Argued and judgment reserved", TODAY));
        assertNull(LitigationRules.heldProblem(TODAY.minusDays(3), "Argued and judgment reserved", TODAY));
    }

    @Test
    void anOrderNeedsADateASummaryAndAComplianceDateNotBeforeIt() {
        assertNull(LitigationRules.orderProblem(TODAY, "Defendant to deliver the plea", null, TODAY));
        assertNull(LitigationRules.orderProblem(TODAY, "Defendant to deliver the plea", TODAY.plusDays(10), TODAY));
        assertNotNull(LitigationRules.orderProblem(TODAY, "Defendant to deliver the plea", TODAY.minusDays(1), TODAY));
        assertNotNull(LitigationRules.orderProblem(TODAY.plusDays(1), "Defendant to deliver the plea", null, TODAY));
        assertNotNull(LitigationRules.orderProblem(null, "Defendant to deliver the plea", null, TODAY));
        assertNotNull(LitigationRules.orderProblem(TODAY, "plea", null, TODAY));
    }

    @Test
    void complianceCannotPredateTheOrderOrBeInTheFuture() {
        LocalDate order = TODAY.minusDays(10);
        assertNull(LitigationRules.compliedProblem(order, TODAY, TODAY));
        assertNotNull(LitigationRules.compliedProblem(order, order.minusDays(1), TODAY));
        assertNotNull(LitigationRules.compliedProblem(order, TODAY.plusDays(1), TODAY));
        assertNotNull(LitigationRules.compliedProblem(order, null, TODAY));
    }

    @Test
    void calendarDescriptionsAreReadable() {
        assertEquals("Trial: 123/2026", LitigationRules.hearingKeyDescription("TRIAL", "123/2026", "High Court"));
        assertEquals("Motion: High Court", LitigationRules.hearingKeyDescription("MOTION", null, "High Court"));
        assertEquals("Comply with court order: Deliver the plea", LitigationRules.complianceKeyDescription("  Deliver   the plea "));
        assertTrue(LitigationRules.complianceKeyDescription("x".repeat(300)).length() < 140);
    }
}
