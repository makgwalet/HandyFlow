package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LeavePolicyRulesTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void removingTheLimitIsAlwaysAllowed() {
        assertThat(LeavePolicyRules.policyProblem(null, null, null, 2026)).isNull();
    }

    @Test
    void aLimitNeedsAFutureYearAndTheAgreement() {
        assertThat(LeavePolicyRules.policyProblem(d("5"), 2027, "Leave policy 2026", 2026)).isNull();
        assertThat(LeavePolicyRules.policyProblem(d("5"), 2026, "Leave policy 2026", 2026)).contains("next year");
        assertThat(LeavePolicyRules.policyProblem(d("5"), 2025, "Leave policy 2026", 2026)).contains("next year");
        assertThat(LeavePolicyRules.policyProblem(d("5"), null, "Leave policy 2026", 2026)).contains("which year");
        assertThat(LeavePolicyRules.policyProblem(d("5"), 2027, null, 2026)).contains("written agreement");
        assertThat(LeavePolicyRules.policyProblem(d("5"), 2027, "   ", 2026)).contains("written agreement");
        assertThat(LeavePolicyRules.policyProblem(d("5"), 2040, "x", 2026)).contains("too far");
    }

    @Test
    void theLimitMustBeSensible() {
        assertThat(LeavePolicyRules.policyProblem(d("0"), 2027, "x", 2026)).isNull();
        assertThat(LeavePolicyRules.policyProblem(d("-1"), 2027, "x", 2026)).contains("negative");
        assertThat(LeavePolicyRules.policyProblem(d("101"), 2027, "x", 2026)).contains("more than 100");
        assertThat(LeavePolicyRules.policyProblem(d("5.123"), 2027, "x", 2026)).contains("two decimals");
    }

    @Test
    void theLimitOnlyAppliesFromItsYear() {
        assertThat(LeavePolicyRules.capFor(d("5"), 2027, 2027)).isEqualByComparingTo(d("5"));
        assertThat(LeavePolicyRules.capFor(d("5"), 2027, 2028)).isEqualByComparingTo(d("5"));
        assertThat(LeavePolicyRules.capFor(d("5"), 2027, 2026)).isNull();
        assertThat(LeavePolicyRules.capFor(null, 2027, 2028)).isNull();
    }

    @Test
    void onlyTheExcessIsForfeited() {
        assertThat(LeavePolicyRules.forfeited(d("12.5"), d("5"))).isEqualByComparingTo(d("7.5"));
        assertThat(LeavePolicyRules.forfeited(d("5"), d("5"))).isEqualByComparingTo(d("0"));
        assertThat(LeavePolicyRules.forfeited(d("3"), d("5"))).isEqualByComparingTo(d("0"));
        assertThat(LeavePolicyRules.forfeited(d("-4"), d("5"))).isEqualByComparingTo(d("0"));
        assertThat(LeavePolicyRules.forfeited(d("12"), null)).isEqualByComparingTo(d("0"));
    }

    @Test
    void riskCountsWhatIsStillToBeEarned() {
        assertThat(LeavePolicyRules.atRisk(d("4"), d("3"), d("5"))).isEqualByComparingTo(d("2"));
        assertThat(LeavePolicyRules.atRisk(d("2"), d("1"), d("5"))).isEqualByComparingTo(d("0"));
        assertThat(LeavePolicyRules.atRisk(d("20"), d("0"), null)).isEqualByComparingTo(d("0"));
    }

    @Test
    void theNoteNamesTheLimitAndTheAgreement() {
        assertThat(LeavePolicyRules.forfeitNote(d("5.00"), "Leave policy 2026", 2026)).contains("5-day limit").contains("2027").contains("Leave policy 2026");
    }
}
