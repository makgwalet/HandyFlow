package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WorkplaceRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);

    @Test
    void placementNeedsOpenEnrolmentNamesAndSaneDays() {
        assertNull(WorkplaceRules.placementProblem("IN_PROGRESS", "Acme", "Ms Mentor", START, null, START));
        assertNull(WorkplaceRules.placementProblem("ENROLLED", "Acme", "Ms Mentor", START, START.plusDays(30), START));
        assertNotNull(WorkplaceRules.placementProblem("CANCELLED", "Acme", "Ms Mentor", START, null, START));
        assertNotNull(WorkplaceRules.placementProblem("ON_HOLD", "Acme", "Ms Mentor", START, null, START));
        assertNotNull(WorkplaceRules.placementProblem("IN_PROGRESS", "A", "Ms Mentor", START, null, START));
        assertNotNull(WorkplaceRules.placementProblem("IN_PROGRESS", "Acme", " ", START, null, START));
        assertNotNull(WorkplaceRules.placementProblem("IN_PROGRESS", "Acme", "Ms Mentor", START, START.minusDays(1), START));
        assertNotNull(WorkplaceRules.placementProblem("IN_PROGRESS", "Acme", "Ms Mentor", START.minusDays(1), null, START));
    }

    @Test
    void statusFlow() {
        assertNull(WorkplaceRules.startProblem("PLANNED", "IN_PROGRESS", TODAY, START, TODAY));
        assertNotNull(WorkplaceRules.startProblem("ACTIVE", "IN_PROGRESS", TODAY, START, TODAY));
        assertNotNull(WorkplaceRules.startProblem("PLANNED", "IN_PROGRESS", TODAY.plusDays(1), START, TODAY));
        assertNull(WorkplaceRules.completeProblem("ACTIVE", TODAY, START, TODAY));
        assertNotNull(WorkplaceRules.completeProblem("PLANNED", TODAY, START, TODAY));
        assertNotNull(WorkplaceRules.completeProblem("ACTIVE", START.minusDays(1), START, TODAY));
        assertNull(WorkplaceRules.cancelProblem("PLANNED", "Host withdrew the offer"));
        assertNull(WorkplaceRules.cancelProblem("ACTIVE", "Host withdrew the offer"));
        assertNotNull(WorkplaceRules.cancelProblem("ACTIVE", "short"));
        assertNotNull(WorkplaceRules.cancelProblem("COMPLETED", "Host withdrew the offer"));
        assertNotNull(WorkplaceRules.editProblem("COMPLETED"));
    }

    @Test
    void logEntryChecks() {
        BigDecimal h8 = BigDecimal.valueOf(8);
        String act = "Assisted with stock counts and shelving";
        assertNull(WorkplaceRules.logProblem("ACTIVE", TODAY, START, null, TODAY, h8, act, false));
        assertNotNull(WorkplaceRules.logProblem("PLANNED", TODAY, START, null, TODAY, h8, act, false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", TODAY.plusDays(1), START, null, TODAY, h8, act, false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", START.minusDays(1), START, null, TODAY, h8, act, false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", TODAY, START, TODAY.minusDays(1), TODAY, h8, act, false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", TODAY, START, null, TODAY, BigDecimal.ZERO, act, false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", TODAY, START, null, TODAY, BigDecimal.valueOf(25), act, false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", TODAY, START, null, TODAY, h8, "short", false));
        assertNotNull(WorkplaceRules.logProblem("ACTIVE", TODAY, START, null, TODAY, h8, act, true));
    }

    @Test
    void confirmationNamesTheMentorOnce() {
        assertNull(WorkplaceRules.confirmProblem("RECORDED", false, TODAY, TODAY.minusDays(1), TODAY, "Ms Mentor"));
        assertNotNull(WorkplaceRules.confirmProblem("VOID", false, TODAY, TODAY.minusDays(1), TODAY, "Ms Mentor"));
        assertNotNull(WorkplaceRules.confirmProblem("RECORDED", true, TODAY, TODAY.minusDays(1), TODAY, "Ms Mentor"));
        assertNotNull(WorkplaceRules.confirmProblem("RECORDED", false, TODAY.minusDays(2), TODAY.minusDays(1), TODAY, "Ms Mentor"));
        assertNotNull(WorkplaceRules.confirmProblem("RECORDED", false, TODAY, TODAY.minusDays(1), TODAY, " "));
        assertNull(WorkplaceRules.voidLogProblem("RECORDED", "Entered against the wrong day"));
        assertNotNull(WorkplaceRules.voidLogProblem("VOID", "Entered against the wrong day"));
    }

    @Test
    void summaryIsATimeRecordNotAnOutcome() {
        String s = WorkplaceRules.summary(new BigDecimal("16.00"), new BigDecimal("8.50"));
        assertTrue(s.contains("24.5 hours logged"));
        assertTrue(s.contains("16 confirmed"));
        assertTrue(s.contains("not a competency decision"));
    }
}
