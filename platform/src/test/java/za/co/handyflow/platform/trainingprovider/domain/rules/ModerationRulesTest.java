package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ModerationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final LocalDate DECIDED = LocalDate.of(2027, 3, 5);
    private static final String BASIS = "Sampled the evidence and checked it against the criteria";
    private static final PractitionerRules.Standing OK = PractitionerRules.Standing.VERIFIED_IN_FORCE;

    private ModerationRules.Facts facts() {
        return new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, true, OK, false, false, false);
    }

    @Test
    void validModerationHasNoProblem() {
        assertNull(ModerationRules.recordProblem(facts(), "CONFIRMED", BASIS, TODAY, TODAY));
        assertNull(ModerationRules.recordProblem(facts(), "QUERIED", BASIS, DECIDED, TODAY));
    }

    @Test
    void independenceIsEnforced() {
        ModerationRules.Facts same = new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, true, OK, true, false, false);
        ModerationRules.Facts involved = new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, true, OK, false, true, false);
        ModerationRules.Facts decider = new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, true, OK, false, false, true);
        assertNotNull(ModerationRules.recordProblem(same, "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(involved, "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(decider, "CONFIRMED", BASIS, TODAY, TODAY));
    }

    @Test
    void theAttemptMustBeLiveDecidedModeratedAndNotYetModerated() {
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("VOID", true, DECIDED, true, false, true, OK, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("RECORDED", false, null, true, false, true, OK, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("RECORDED", true, DECIDED, false, false, true, OK, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("RECORDED", true, DECIDED, true, true, true, OK, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
    }

    @Test
    void theModeratorMustBeActiveAndCheckedInForce() {
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, false, OK, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, true, PractitionerRules.Standing.NOT_VERIFIED, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(new ModerationRules.Facts("RECORDED", true, DECIDED, true, false, true, PractitionerRules.Standing.EXPIRED, false, false, false), "CONFIRMED", BASIS, TODAY, TODAY));
    }

    @Test
    void outcomeBasisAndDayAreChecked() {
        assertNotNull(ModerationRules.recordProblem(facts(), "APPROVED", BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(facts(), null, BASIS, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(facts(), "CONFIRMED", "short", TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(facts(), "CONFIRMED", null, TODAY, TODAY));
        assertNotNull(ModerationRules.recordProblem(facts(), "CONFIRMED", BASIS, null, TODAY));
        assertNotNull(ModerationRules.recordProblem(facts(), "CONFIRMED", BASIS, TODAY.plusDays(1), TODAY));
        assertNotNull(ModerationRules.recordProblem(facts(), "CONFIRMED", BASIS, DECIDED.minusDays(1), TODAY));
    }

    @Test
    void onlyAnOpenQueryIsResolvedWithAWrittenNote() {
        String note = "Attempt voided and the learner reassessed on a new day";
        assertNull(ModerationRules.resolveProblem("QUERIED", false, note));
        assertNotNull(ModerationRules.resolveProblem("CONFIRMED", false, note));
        assertNotNull(ModerationRules.resolveProblem("QUERIED", true, note));
        assertNotNull(ModerationRules.resolveProblem("QUERIED", false, "short"));
        assertNotNull(ModerationRules.resolveProblem("QUERIED", false, null));
    }
}
