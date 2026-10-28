package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterWorkflowRulesTest {

    private static final BigDecimal SALARY = new BigDecimal("35000");

    private static String move(String from, String to) {
        return RecruiterWorkflowRules.stageMoveProblem(from, to, null, SALARY, false, "NOT_SUBMITTED");
    }

    @Test
    void liveStagesMoveFreelyBothWays() {
        assertThat(move("APPLIED", "SCREENING")).isNull();
        assertThat(move("SCREENING", "INTERVIEW")).isNull();
        assertThat(move("INTERVIEW", "ASSESSMENT")).isNull();
        assertThat(move("INTERVIEW", "SCREENING")).isNull();
        assertThat(move("ASSESSMENT", "OFFER")).isNull();
    }

    @Test
    void anUnknownOrBlankStageIsRefused() {
        assertThat(move("APPLIED", "FOO")).isEqualTo("Choose a valid stage.");
        assertThat(move("APPLIED", null)).isEqualTo("Choose a valid stage.");
        assertThat(move("APPLIED", " ")).isEqualTo("Choose a valid stage.");
        assertThat(move("APPLIED", "WITHDRAWN")).isEqualTo("Choose a valid stage.");
    }

    @Test
    void movingToTheSameStageIsRefused() {
        assertThat(move("INTERVIEW", "INTERVIEW")).isEqualTo("The application is already in that stage.");
        assertThat(move("INTERVIEW", "interview")).isEqualTo("The application is already in that stage.");
    }

    @Test
    void rejectingNeedsAReason() {
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "REJECTED", null, null, false, null)).contains("reason for rejecting");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "REJECTED", "  ", null, false, null)).contains("reason for rejecting");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "REJECTED", "Not enough experience", null, false, null)).isNull();
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "REJECTED", "x".repeat(501), null, false, null)).contains("too long");
    }

    @Test
    void anOfferNeedsASalary() {
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "OFFER", null, null, false, null)).contains("offered salary");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "OFFER", null, BigDecimal.ZERO, false, null)).contains("offered salary");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "OFFER", null, new BigDecimal("-1"), false, null)).contains("offered salary");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("INTERVIEW", "OFFER", null, SALARY, false, null)).isNull();
    }

    @Test
    void hiringNeedsAnOfferFirst() {
        assertThat(move("APPLIED", "HIRED")).isEqualTo("Move the application to Offer before marking it hired.");
        assertThat(move("INTERVIEW", "HIRED")).contains("before marking it hired");
        assertThat(move("OFFER", "HIRED")).isNull();
    }

    @Test
    void whenTheCompanyRequiresApprovalAnUnapprovedOfferCannotBeHired() {
        assertThat(RecruiterWorkflowRules.stageMoveProblem("OFFER", "HIRED", null, SALARY, true, "NOT_SUBMITTED")).contains("approved");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("OFFER", "HIRED", null, SALARY, true, "SUBMITTED")).contains("approved");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("OFFER", "HIRED", null, SALARY, true, "REJECTED")).contains("approved");
        assertThat(RecruiterWorkflowRules.stageMoveProblem("OFFER", "HIRED", null, SALARY, true, "APPROVED")).isNull();
        assertThat(RecruiterWorkflowRules.stageMoveProblem("OFFER", "HIRED", null, SALARY, false, "NOT_SUBMITTED")).isNull();
    }

    @Test
    void anInterviewTimeInThePastIsRefusedButNoTimeIsFine() {
        Instant now = Instant.parse("2026-10-28T10:00:00Z");
        assertThat(RecruiterWorkflowRules.scheduleProblem(null, now, false)).isNull();
        assertThat(RecruiterWorkflowRules.scheduleProblem(now.plus(1, ChronoUnit.DAYS), now, false)).isNull();
        assertThat(RecruiterWorkflowRules.scheduleProblem(now.minus(5, ChronoUnit.MINUTES), now, false)).isNull();
        assertThat(RecruiterWorkflowRules.scheduleProblem(now.minus(1, ChronoUnit.HOURS), now, false)).isEqualTo("The interview time is in the past.");
    }

    @Test
    void theSameTimeTwiceIsRefused() {
        Instant now = Instant.parse("2026-10-28T10:00:00Z");
        assertThat(RecruiterWorkflowRules.scheduleProblem(now.plus(1, ChronoUnit.DAYS), now, true)).contains("already booked");
    }

    @Test
    void anOfferLetterGoesOutOnlyAtTheOfferStage() {
        assertThat(RecruiterWorkflowRules.offerSendProblem("OFFER")).isNull();
        assertThat(RecruiterWorkflowRules.offerSendProblem("INTERVIEW")).contains("Offer stage");
        assertThat(RecruiterWorkflowRules.offerSendProblem("HIRED")).contains("Offer stage");
        assertThat(RecruiterWorkflowRules.offerSendProblem("REJECTED")).contains("Offer stage");
    }
}
