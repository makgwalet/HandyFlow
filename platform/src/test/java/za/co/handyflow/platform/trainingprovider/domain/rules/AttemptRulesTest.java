package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttemptRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final LocalDate START = LocalDate.of(2027, 2, 1);
    private static final UUID V = UUID.randomUUID();
    private static final AttemptRules.UUIDPair SAME = new AttemptRules.UUIDPair(V, V);

    private String problem(String instrument, AttemptRules.UUIDPair v, String member, String cohort, LocalDate on, boolean active,
                           PractitionerRules.Standing s, AttemptRules.Prior prior) {
        return AttemptRules.attemptProblem(instrument, v, member, cohort, START, on, TODAY, active, s, prior);
    }

    @Test
    void validAttemptHasNoProblem() {
        assertNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY, true, PractitionerRules.Standing.VERIFIED_IN_FORCE, AttemptRules.Prior.NONE));
        assertNull(problem("APPROVED", SAME, "ACTIVE", "COMPLETED", START, true, PractitionerRules.Standing.VERIFIED_IN_FORCE, AttemptRules.Prior.NONE));
    }

    @Test
    void eachBlockerIsReported() {
        PractitionerRules.Standing ok = PractitionerRules.Standing.VERIFIED_IN_FORCE;
        assertNotNull(problem("DRAFT", SAME, "ACTIVE", "RUNNING", TODAY, true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("RETIRED", SAME, "ACTIVE", "RUNNING", TODAY, true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", new AttemptRules.UUIDPair(V, UUID.randomUUID()), "ACTIVE", "RUNNING", TODAY, true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "REMOVED", "RUNNING", TODAY, true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "PLANNED", TODAY, true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "CANCELLED", TODAY, true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY.plusDays(1), true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", START.minusDays(1), true, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY, false, ok, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY, true, PractitionerRules.Standing.NOT_VERIFIED, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY, true, PractitionerRules.Standing.EXPIRED, AttemptRules.Prior.NONE));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY, true, ok, AttemptRules.Prior.OPEN_ATTEMPT));
        assertNotNull(problem("APPROVED", SAME, "ACTIVE", "RUNNING", TODAY, true, ok, AttemptRules.Prior.ALREADY_COMPETENT));
    }

    @Test
    void markFollowsTheInstrumentScale() {
        BigDecimal fifty = new BigDecimal("50");
        assertNull(AttemptRules.markProblem(fifty, new BigDecimal("0")));
        assertNull(AttemptRules.markProblem(fifty, new BigDecimal("50")));
        assertNotNull(AttemptRules.markProblem(fifty, new BigDecimal("50.01")));
        assertNotNull(AttemptRules.markProblem(fifty, new BigDecimal("-1")));
        assertNotNull(AttemptRules.markProblem(fifty, null));
        assertNull(AttemptRules.markProblem(null, null));
        assertNotNull(AttemptRules.markProblem(null, new BigDecimal("10")));
    }

    @Test
    void markCorrectionNeedsReasonAndNoDecision() {
        BigDecimal fifty = new BigDecimal("50");
        BigDecimal old = new BigDecimal("30");
        assertNull(AttemptRules.correctionProblem("RECORDED", false, fifty, new BigDecimal("35"), old, "Added up wrongly the first time"));
        assertNotNull(AttemptRules.correctionProblem("RECORDED", true, fifty, new BigDecimal("35"), old, "Added up wrongly the first time"));
        assertNotNull(AttemptRules.correctionProblem("VOID", false, fifty, new BigDecimal("35"), old, "Added up wrongly the first time"));
        assertNotNull(AttemptRules.correctionProblem("RECORDED", false, fifty, new BigDecimal("30.00"), old, "Added up wrongly the first time"));
        assertNotNull(AttemptRules.correctionProblem("RECORDED", false, fifty, new BigDecimal("35"), old, "short"));
        assertNotNull(AttemptRules.correctionProblem("RECORDED", false, fifty, new BigDecimal("60"), old, "Added up wrongly the first time"));
    }

    @Test
    void decisionNeedsABasisAndTheAssessor() {
        String basis = "Observed all steps against the checklist";
        assertNull(AttemptRules.decisionProblem("RECORDED", false, "COMPETENT", basis, false));
        assertNull(AttemptRules.decisionProblem("RECORDED", false, "NOT_YET_COMPETENT", basis, false));
        assertNotNull(AttemptRules.decisionProblem("RECORDED", false, "PASS", basis, false));
        assertNotNull(AttemptRules.decisionProblem("RECORDED", false, null, basis, false));
        assertNotNull(AttemptRules.decisionProblem("RECORDED", false, "COMPETENT", "short", false));
        assertNotNull(AttemptRules.decisionProblem("RECORDED", false, "COMPETENT", null, false));
        assertNotNull(AttemptRules.decisionProblem("RECORDED", true, "COMPETENT", basis, false));
        assertNotNull(AttemptRules.decisionProblem("VOID", false, "COMPETENT", basis, false));
        assertNotNull(AttemptRules.decisionProblem("RECORDED", false, "COMPETENT", basis, true));
    }

    @Test
    void voidNeedsAReasonAndHappensOnce() {
        assertNull(AttemptRules.voidProblem("RECORDED", "Recorded against the wrong learner"));
        assertNotNull(AttemptRules.voidProblem("RECORDED", "short"));
        assertNotNull(AttemptRules.voidProblem("VOID", "Recorded against the wrong learner"));
    }
}
