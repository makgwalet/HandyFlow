package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompletionRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 1);
    private static final LocalDate START = LocalDate.of(2027, 2, 1);
    private static final String BASIS = "All required assessments were completed and moderated";
    private static final String WHY = "Learner moved abroad; remaining task waived by the programme lead";

    private static CompletionRules.AttemptFact fact(String decision, String moderation, boolean resolved) {
        return new CompletionRules.AttemptFact(decision, moderation, resolved);
    }

    @Test
    void noAttemptsMeansNoAttempt() {
        assertEquals("NO_ATTEMPT", CompletionRules.instrumentState(true, List.of()));
    }

    @Test
    void competentNeedsModerationWhenTheInstrumentIsModerated() {
        assertEquals("WAITING_DECISION", CompletionRules.instrumentState(true, List.of(fact(null, null, false))));
        assertEquals("NOT_YET_COMPETENT", CompletionRules.instrumentState(true, List.of(fact("NOT_YET_COMPETENT", null, false))));
        assertEquals("WAITING_MODERATION", CompletionRules.instrumentState(true, List.of(fact("COMPETENT", null, false))));
        assertEquals("QUERY_OPEN", CompletionRules.instrumentState(true, List.of(fact("COMPETENT", "QUERIED", false))));
        assertEquals("COMPETENT", CompletionRules.instrumentState(true, List.of(fact("COMPETENT", "QUERIED", true))));
        assertEquals("COMPETENT", CompletionRules.instrumentState(true, List.of(fact("COMPETENT", "CONFIRMED", false))));
    }

    @Test
    void anUnmoderatedInstrumentIsCompetentOnTheDecisionAlone() {
        assertEquals("COMPETENT", CompletionRules.instrumentState(false, List.of(fact("COMPETENT", null, false))));
    }

    @Test
    void aRetakeThatSucceedsOutweighsAnEarlierFailure() {
        assertEquals("COMPETENT", CompletionRules.instrumentState(true, List.of(fact("NOT_YET_COMPETENT", null, false), fact("COMPETENT", "CONFIRMED", false))));
        assertEquals("WAITING_DECISION", CompletionRules.instrumentState(true, List.of(fact("NOT_YET_COMPETENT", null, false), fact(null, null, false))));
    }

    @Test
    void completingWithNothingOpenNeedsOnlyABasis() {
        assertNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY, TODAY, BASIS, 0, null));
        assertNull(CompletionRules.recordProblem("ENROLLED", START, false, "COMPLETED", START, TODAY, BASIS, 0, null));
    }

    @Test
    void completingWithGapsNeedsAWrittenReason() {
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY, TODAY, BASIS, 2, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY, TODAY, BASIS, 1, "too short"));
        assertNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY, TODAY, BASIS, 2, WHY));
    }

    @Test
    void didNotCompleteNeedsNoOverrideAndAllowsAnyEnrolmentStatus() {
        assertNull(CompletionRules.recordProblem("ON_HOLD", START, false, "DID_NOT_COMPLETE", TODAY, TODAY, BASIS, 3, null));
        assertNull(CompletionRules.recordProblem("WITHDRAWN", START, false, "DID_NOT_COMPLETE", TODAY, TODAY, BASIS, 0, null));
    }

    @Test
    void otherBlockers() {
        assertNotNull(CompletionRules.recordProblem("ON_HOLD", START, false, "COMPLETED", TODAY, TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("CANCELLED", START, false, "COMPLETED", TODAY, TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, true, "COMPLETED", TODAY, TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "PASSED", TODAY, TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, null, TODAY, TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", null, TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY.plusDays(1), TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", START.minusDays(1), TODAY, BASIS, 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY, TODAY, "short", 0, null));
        assertNotNull(CompletionRules.recordProblem("IN_PROGRESS", START, false, "COMPLETED", TODAY, TODAY, null, 0, null));
    }

    @Test
    void withdrawalNeedsAReasonAndHappensOnce() {
        assertNull(CompletionRules.withdrawProblem("RECORDED", "Recorded against the wrong enrolment"));
        assertNotNull(CompletionRules.withdrawProblem("RECORDED", "short"));
        assertNotNull(CompletionRules.withdrawProblem("WITHDRAWN", "Recorded against the wrong enrolment"));
    }
}
