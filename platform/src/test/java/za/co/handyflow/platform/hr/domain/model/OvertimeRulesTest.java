package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OvertimeRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final LocalDate PAST = LocalDate.of(2026, 11, 9);

    @Test
    void aNewDayIsCreatedAndAnApprovedOneIsNeverTouched() {
        assertThat(OvertimeRules.onRequest(null, 0, 60)).isEqualTo(OvertimeRules.Action.CREATE);
        assertThat(OvertimeRules.onRequest("APPROVED", 60, 120)).isEqualTo(OvertimeRules.Action.KEEP);
    }

    @Test
    void aWaitingClaimFollowsTheRecord() {
        assertThat(OvertimeRules.onRequest("PENDING", 60, 60)).isEqualTo(OvertimeRules.Action.KEEP);
        assertThat(OvertimeRules.onRequest("PENDING", 60, 90)).isEqualTo(OvertimeRules.Action.UPDATE);
    }

    @Test
    void aRejectedClaimIsOnlyClaimedAgainWhenTheHoursChanged() {
        assertThat(OvertimeRules.onRequest("REJECTED", 60, 60)).isEqualTo(OvertimeRules.Action.KEEP);
        assertThat(OvertimeRules.onRequest("REJECTED", 60, 30)).isEqualTo(OvertimeRules.Action.UPDATE);
        assertThat(OvertimeRules.onRequest("WITHDRAWN", 60, 60)).isEqualTo(OvertimeRules.Action.KEEP);
    }

    @Test
    void explainsWhyADayCannotBeClaimed() {
        assertThat(OvertimeRules.claimProblem(null, 0, PAST, TODAY, false, false)).contains("no overtime");
        assertThat(OvertimeRules.claimProblem("PUBLIC_HOLIDAY", 60, PAST, TODAY, false, false)).contains("s 18");
        assertThat(OvertimeRules.claimProblem("OVERTIME", 60, TODAY, TODAY, false, false)).contains("not over");
        assertThat(OvertimeRules.claimProblem("OVERTIME", 60, PAST, TODAY, false, true)).contains("no clock-out");
        assertThat(OvertimeRules.claimProblem("OVERTIME", 60, PAST, TODAY, true, false)).contains("threshold");
        assertThat(OvertimeRules.claimProblem("OVERTIME", 60, PAST, TODAY, false, false)).isNull();
        assertThat(OvertimeRules.claimProblem("SUNDAY", 60, PAST, TODAY, false, false)).isNull();
    }

    @Test
    void nobodyDecidesTheirOwnOvertime() {
        assertThat(OvertimeRules.decisionProblem("PENDING", true, null, false, true)).contains("own overtime");
    }

    @Test
    void onlyAWaitingClaimIsApprovedAndOnlyAWaitingOrApprovedOneRejected() {
        assertThat(OvertimeRules.decisionProblem("PENDING", true, null, false, false)).isNull();
        assertThat(OvertimeRules.decisionProblem("APPROVED", true, null, false, false)).contains("waiting");
        assertThat(OvertimeRules.decisionProblem("REJECTED", true, null, false, false)).contains("waiting");
        assertThat(OvertimeRules.decisionProblem("PENDING", false, "Not authorised", false, false)).isNull();
        assertThat(OvertimeRules.decisionProblem("APPROVED", false, "Wrong day", false, false)).isNull();
        assertThat(OvertimeRules.decisionProblem("REJECTED", false, "again", false, false)).contains("already rejected");
    }

    @Test
    void rejectingNeedsAReasonAndAPaidClaimCannotChange() {
        assertThat(OvertimeRules.decisionProblem("PENDING", false, " ", false, false)).contains("reason");
        assertThat(OvertimeRules.decisionProblem("APPROVED", false, "x", true, false)).contains("pay run");
        assertThat(OvertimeRules.decisionProblem("PENDING", true, "x".repeat(501), false, false)).contains("500");
    }

    @Test
    void onlyWaitingOrRejectedClaimsCanBeWithdrawn() {
        assertThat(OvertimeRules.withdrawProblem("PENDING")).isNull();
        assertThat(OvertimeRules.withdrawProblem("REJECTED")).isNull();
        assertThat(OvertimeRules.withdrawProblem("APPROVED")).isNotNull();
    }

    @Test
    void approvedOrPaidOvertimeLocksTheDay() {
        assertThat(OvertimeRules.lockReason("PENDING", false)).isNull();
        assertThat(OvertimeRules.lockReason("REJECTED", false)).isNull();
        assertThat(OvertimeRules.lockReason("APPROVED", false)).contains("approved");
        assertThat(OvertimeRules.lockReason("APPROVED", true)).contains("pay run");
    }
}
