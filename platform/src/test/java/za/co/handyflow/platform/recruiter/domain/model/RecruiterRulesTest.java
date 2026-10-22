package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterRulesTest {

    private static final UUID ME = UUID.randomUUID(), YOU = UUID.randomUUID();
    private static BigDecimal n(String s) { return new BigDecimal(s); }

    // ── Jobs ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    void onlyADraftNotAlreadyWaitingOrApprovedCanBeSentForApproval() {
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "NOT_SUBMITTED", "Clerk", "Does filing", n("1"), n("2"))).isNull();
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "REJECTED", "Clerk", "Does filing", null, null)).isNull();
        assertThat(RecruiterApprovalRules.submitJobProblem("OPEN", "NOT_SUBMITTED", "Clerk", "x", null, null)).contains("draft");
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "SUBMITTED", "Clerk", "x", null, null)).contains("already waiting");
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "APPROVED", "Clerk", "x", null, null)).contains("already approved");
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "NOT_SUBMITTED", " ", "x", null, null)).contains("title");
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "NOT_SUBMITTED", "Clerk", "", null, null)).contains("description");
        assertThat(RecruiterApprovalRules.submitJobProblem("DRAFT", "NOT_SUBMITTED", "Clerk", "x", n("20"), n("10"))).contains("above");
    }

    @Test
    void theSubmitterCannotDecideAndANoIsExplained() {
        assertThat(RecruiterApprovalRules.decideProblem("SUBMITTED", ME, YOU, true, null)).isNull();
        assertThat(RecruiterApprovalRules.decideProblem("SUBMITTED", ME, ME, true, null)).contains("yourself");
        assertThat(RecruiterApprovalRules.decideProblem("SUBMITTED", ME, YOU, false, "  ")).contains("why");
        assertThat(RecruiterApprovalRules.decideProblem("SUBMITTED", ME, YOU, false, "Budget not agreed")).isNull();
        assertThat(RecruiterApprovalRules.decideProblem("APPROVED", ME, YOU, true, null)).contains("Nothing is waiting");
        assertThat(RecruiterApprovalRules.decideProblem("SUBMITTED", ME, null, true, null)).contains("identified");
        assertThat(RecruiterApprovalRules.decideProblem("SUBMITTED", ME, YOU, true, "x".repeat(501))).contains("too long");
    }

    @Test
    void publishingNeedsApprovalOnlyWhenTheCompanyAsksForIt() {
        assertThat(RecruiterApprovalRules.publishProblem(false, "NOT_SUBMITTED")).isNull();
        assertThat(RecruiterApprovalRules.publishProblem(true, "APPROVED")).isNull();
        assertThat(RecruiterApprovalRules.publishProblem(true, "NOT_SUBMITTED")).contains("approved before");
        assertThat(RecruiterApprovalRules.publishProblem(true, "SUBMITTED")).contains("waiting");
        assertThat(RecruiterApprovalRules.publishProblem(true, "REJECTED")).contains("not approved");
    }

    @Test
    void changingADraftTakesTheApprovalAwayButChangingALiveJobDoesNot() {
        assertThat(RecruiterApprovalRules.editWithdrawsApproval("DRAFT", "APPROVED")).isTrue();
        assertThat(RecruiterApprovalRules.editWithdrawsApproval("DRAFT", "SUBMITTED")).isTrue();
        assertThat(RecruiterApprovalRules.editWithdrawsApproval("DRAFT", "NOT_SUBMITTED")).isFalse();
        assertThat(RecruiterApprovalRules.editWithdrawsApproval("OPEN", "APPROVED")).isFalse();
    }

    // ── Offers ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void anOfferNeedsTermsAndTheOfferStageBeforeItIsSentForApproval() {
        assertThat(RecruiterApprovalRules.submitOfferProblem("OFFER", "NOT_SUBMITTED", n("30000"), false)).isNull();
        assertThat(RecruiterApprovalRules.submitOfferProblem("INTERVIEW", "NOT_SUBMITTED", n("30000"), false)).contains("offer stage");
        assertThat(RecruiterApprovalRules.submitOfferProblem("OFFER", "NOT_SUBMITTED", null, false)).contains("salary");
        assertThat(RecruiterApprovalRules.submitOfferProblem("OFFER", "NOT_SUBMITTED", n("0"), false)).contains("salary");
        assertThat(RecruiterApprovalRules.submitOfferProblem("OFFER", "NOT_SUBMITTED", n("30000"), true)).contains("already sent");
        assertThat(RecruiterApprovalRules.submitOfferProblem("OFFER", "SUBMITTED", n("30000"), false)).contains("already waiting");
        assertThat(RecruiterApprovalRules.submitOfferProblem("OFFER", "APPROVED", n("30000"), false)).contains("already approved");
    }

    @Test
    void anOfferLetterNeedsApprovalOnlyWhenTheCompanyAsksForIt() {
        assertThat(RecruiterApprovalRules.sendOfferProblem(false, "NOT_SUBMITTED")).isNull();
        assertThat(RecruiterApprovalRules.sendOfferProblem(true, "APPROVED")).isNull();
        assertThat(RecruiterApprovalRules.sendOfferProblem(true, "NOT_SUBMITTED")).contains("approved before");
        assertThat(RecruiterApprovalRules.sendOfferProblem(true, "SUBMITTED")).contains("waiting");
        assertThat(RecruiterApprovalRules.sendOfferProblem(true, "REJECTED")).contains("not approved");
    }

    @Test
    void differentTermsAreADifferentOffer() {
        LocalDate d = LocalDate.of(2026, 11, 1);
        assertThat(RecruiterApprovalRules.offerChanged(n("30000"), "MONTHLY", d, "Medical", n("30000.00"), "MONTHLY", d, "Medical")).isFalse();
        assertThat(RecruiterApprovalRules.offerChanged(n("30000"), "MONTHLY", d, null, n("30000"), "MONTHLY", d, " ")).isFalse();
        assertThat(RecruiterApprovalRules.offerChanged(n("30000"), "MONTHLY", d, null, n("31000"), "MONTHLY", d, null)).isTrue();
        assertThat(RecruiterApprovalRules.offerChanged(n("30000"), "MONTHLY", d, null, n("30000"), "WEEKLY", d, null)).isTrue();
        assertThat(RecruiterApprovalRules.offerChanged(n("30000"), "MONTHLY", d, null, n("30000"), "MONTHLY", d.plusDays(1), null)).isTrue();
        assertThat(RecruiterApprovalRules.offerChanged(n("30000"), "MONTHLY", d, null, n("30000"), "MONTHLY", d, "Car allowance")).isTrue();
        assertThat(RecruiterApprovalRules.offerChanged(null, null, null, null, n("30000"), "MONTHLY", d, null)).isTrue();
        assertThat(RecruiterApprovalRules.offerChanged(null, null, null, null, null, null, null, null)).isFalse();
    }

    // ── Retention ─────────────────────────────────────────────────────────────────────────────────

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
    private static Instant ago(int months) { return NOW.atZone(java.time.ZoneId.of("Africa/Johannesburg")).minusMonths(months).minusDays(1).toInstant(); }

    @Test
    void theRetentionPeriodIsBetweenThreeAndSixtyMonths() {
        assertThat(RecruiterRetentionRules.monthsProblem(12)).isNull();
        assertThat(RecruiterRetentionRules.monthsProblem(3)).isNull();
        assertThat(RecruiterRetentionRules.monthsProblem(60)).isNull();
        assertThat(RecruiterRetentionRules.monthsProblem(2)).contains("between 3 and 60");
        assertThat(RecruiterRetentionRules.monthsProblem(61)).contains("between 3 and 60");
        assertThat(RecruiterRetentionRules.monthsProblem(null)).contains("between 3 and 60");
    }

    @Test
    void anUnsuccessfulApplicantPastThePeriodMayBeRemoved() {
        var apps = List.of(new RecruiterRetentionRules.App("REJECTED", false, ago(13)), new RecruiterRetentionRules.App("WITHDRAWN", false, ago(14)));
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(13), apps, 12, NOW)).isNull();
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(13), List.of(), 12, NOW)).isNull();
    }

    @Test
    void anApplicantWithALiveApplicationOrAHireIsKept() {
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(20), List.of(new RecruiterRetentionRules.App("INTERVIEW", false, ago(20))), 12, NOW)).contains("still open");
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(20), List.of(new RecruiterRetentionRules.App("HIRED", true, ago(20))), 12, NOW)).contains("hired");
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(20), List.of(new RecruiterRetentionRules.App("REJECTED", true, ago(20))), 12, NOW)).contains("hired");
    }

    @Test
    void theClockRunsFromTheMostRecentActivityNotTheFirst() {
        var recent = List.of(new RecruiterRetentionRules.App("REJECTED", false, ago(2)));
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(30), recent, 12, NOW)).contains("has not ended");
        assertThat(RecruiterRetentionRules.removalProblem(false, ago(2), List.of(), 12, NOW)).contains("has not ended");
    }

    @Test
    void anApplicantAlreadyRemovedIsNotRemovedAgain() {
        assertThat(RecruiterRetentionRules.removalProblem(true, ago(20), List.of(), 12, NOW)).contains("already removed");
    }
}
