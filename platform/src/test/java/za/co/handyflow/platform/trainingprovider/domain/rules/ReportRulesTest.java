package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReportRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);

    @Test
    void obligationNeedsASourceNoteAndSaneSettings() {
        assertNull(ReportRules.obligationProblem("Quarterly learner report", "QUARTERLY", 30, "Take from the authority's letter of 3 May"));
        assertNotNull(ReportRules.obligationProblem("Q", "QUARTERLY", 30, "Take from the authority's letter"));
        assertNotNull(ReportRules.obligationProblem("Report", "WEEKLY", 30, "Take from the authority's letter"));
        assertNotNull(ReportRules.obligationProblem("Report", "ANNUAL", -1, "Take from the authority's letter"));
        assertNotNull(ReportRules.obligationProblem("Report", "ANNUAL", 400, "Take from the authority's letter"));
        assertNotNull(ReportRules.obligationProblem("Report", "ANNUAL", 30, "short"));
        assertNotNull(ReportRules.obligationProblem("Report", "ANNUAL", null, "Take from the authority's letter"));
    }

    @Test
    void dueDayIsComputedUnlessOverriddenWithAReason() {
        LocalDate s = LocalDate.of(2027, 1, 1);
        LocalDate e = LocalDate.of(2027, 3, 31);
        LocalDate due = ReportRules.computedDue(e, 30);
        assertEquals(LocalDate.of(2027, 4, 30), due);
        assertNull(ReportRules.periodProblem(true, false, s, e, due, due, null));
        assertNotNull(ReportRules.periodProblem(true, false, s, e, due, due.plusDays(5), null));
        assertNull(ReportRules.periodProblem(true, false, s, e, due, due.plusDays(5), "Authority extended the deadline"));
        assertNotNull(ReportRules.periodProblem(false, false, s, e, due, due, null));
        assertNotNull(ReportRules.periodProblem(true, true, s, e, due, due, null));
        assertNotNull(ReportRules.periodProblem(true, false, e, s, due, due, null));
    }

    @Test
    void submitAcknowledgeWaiveVoid() {
        LocalDate start = LocalDate.of(2027, 1, 1);
        assertNull(ReportRules.submitProblem("OPEN", TODAY, start, TODAY));
        assertNotNull(ReportRules.submitProblem("SUBMITTED", TODAY, start, TODAY));
        assertNotNull(ReportRules.submitProblem("OPEN", TODAY.plusDays(1), start, TODAY));
        assertNotNull(ReportRules.submitProblem("OPEN", start.minusDays(1), start, TODAY));
        assertNull(ReportRules.acknowledgeProblem("SUBMITTED", false, TODAY, TODAY.minusDays(2), TODAY));
        assertNotNull(ReportRules.acknowledgeProblem("OPEN", false, TODAY, null, TODAY));
        assertNotNull(ReportRules.acknowledgeProblem("SUBMITTED", true, TODAY, TODAY.minusDays(2), TODAY));
        assertNotNull(ReportRules.acknowledgeProblem("SUBMITTED", false, TODAY.minusDays(3), TODAY.minusDays(2), TODAY));
        assertNull(ReportRules.waiveProblem("OPEN", "Authority exempted this window"));
        assertNotNull(ReportRules.waiveProblem("SUBMITTED", "Authority exempted this window"));
        assertNotNull(ReportRules.waiveProblem("OPEN", "short"));
        assertNull(ReportRules.voidProblem("SUBMITTED", "Submitted against the wrong period"));
        assertNotNull(ReportRules.voidProblem("VOID", "Submitted against the wrong period"));
    }

    @Test
    void overdueIsWorkedOutNotStored() {
        assertEquals("OVERDUE", ReportRules.dueState("OPEN", TODAY.minusDays(1), TODAY, 14));
        assertEquals("DUE_SOON", ReportRules.dueState("OPEN", TODAY, TODAY, 14));
        assertEquals("DUE_SOON", ReportRules.dueState("OPEN", TODAY.plusDays(14), TODAY, 14));
        assertEquals("OPEN_LATER", ReportRules.dueState("OPEN", TODAY.plusDays(15), TODAY, 14));
        assertEquals("SUBMITTED", ReportRules.dueState("SUBMITTED", TODAY.minusDays(30), TODAY, 14));
        assertEquals("WAIVED", ReportRules.dueState("WAIVED", TODAY.minusDays(30), TODAY, 14));
    }
}
