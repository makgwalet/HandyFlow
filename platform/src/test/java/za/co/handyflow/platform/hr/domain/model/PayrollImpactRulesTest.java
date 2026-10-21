package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.PayrollImpactRules.Facts;
import za.co.handyflow.platform.hr.domain.model.PayrollImpactRules.Verdict;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollImpactRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static Facts facts(boolean rules, boolean emp, boolean ignored, boolean assumed, String paye, String uif, String sdl) {
        return new Facts(rules, emp, ignored, assumed, d(paye), d(uif), d(sdl));
    }

    @Test
    void noDifferenceWhenEverythingMatches() {
        assertThat(PayrollImpactRules.classify(facts(false, false, false, false, "0", "0", "0"))).isEqualTo(Verdict.NO_DIFFERENCE);
        assertThat(PayrollImpactRules.classify(facts(false, false, false, false, "0.004", "0", "0"))).isEqualTo(Verdict.NO_DIFFERENCE);
    }

    @Test
    void aKnownDifferenceIsConfirmed() {
        assertThat(PayrollImpactRules.classify(facts(false, false, false, false, "-364.00", "0", "0"))).isEqualTo(Verdict.CONFIRMED_DIFFERENCE);
        assertThat(PayrollImpactRules.classify(facts(false, false, false, false, "0", "0", "100.00"))).isEqualTo(Verdict.CONFIRMED_DIFFERENCE);
    }

    @Test
    void aDifferenceThatRestsOnAGuessIsOnlyPotential() {
        assertThat(PayrollImpactRules.classify(facts(false, false, false, true, "12.00", "0", "0"))).isEqualTo(Verdict.POTENTIAL_DIFFERENCE);
    }

    @Test
    void anAssumptionWithNoDifferenceIsStillNoDifference() {
        assertThat(PayrollImpactRules.classify(facts(false, false, false, true, "0", "0", "0"))).isEqualTo(Verdict.NO_DIFFERENCE);
    }

    @Test
    void missingRulesOrEmployeeOrIgnoredInputsNeedReview() {
        assertThat(PayrollImpactRules.classify(facts(true, false, false, false, "0", "0", "0"))).isEqualTo(Verdict.REVIEW_REQUIRED);
        assertThat(PayrollImpactRules.classify(facts(false, true, false, false, "50", "0", "0"))).isEqualTo(Verdict.REVIEW_REQUIRED);
        assertThat(PayrollImpactRules.classify(facts(false, false, true, false, "50", "0", "0"))).isEqualTo(Verdict.REVIEW_REQUIRED);
    }

    @Test
    void aRunTakesItsWorstLine() {
        assertThat(PayrollImpactRules.runVerdict(List.of(Verdict.NO_DIFFERENCE, Verdict.POTENTIAL_DIFFERENCE))).isEqualTo(Verdict.POTENTIAL_DIFFERENCE);
        assertThat(PayrollImpactRules.runVerdict(List.of(Verdict.POTENTIAL_DIFFERENCE, Verdict.REVIEW_REQUIRED))).isEqualTo(Verdict.REVIEW_REQUIRED);
        assertThat(PayrollImpactRules.runVerdict(List.of(Verdict.REVIEW_REQUIRED, Verdict.CONFIRMED_DIFFERENCE))).isEqualTo(Verdict.CONFIRMED_DIFFERENCE);
        assertThat(PayrollImpactRules.runVerdict(List.of())).isEqualTo(Verdict.NO_DIFFERENCE);
    }

    @Test
    void reasonsAreInPlainWords() {
        var f = facts(false, false, false, true, "-364.00", "0", "0");
        List<String> r = PayrollImpactRules.reasons(f, 2025, 2026);
        assertThat(r.stream().anyMatch(s -> s.contains("labelled tax year 2025") && s.contains("2026"))).isTrue();
        assertThat(r.stream().anyMatch(s -> s.contains("No medical scheme membership"))).isTrue();
        assertThat(r.stream().anyMatch(s -> s.contains("PAYE differs by -364.00"))).isTrue();
    }

    @Test
    void noMatchingYearLabelMeansNoLabelReason() {
        List<String> r = PayrollImpactRules.reasons(facts(false, false, false, false, "0", "0", "0"), 2026, 2026);
        assertThat(r).isEmpty();
    }
}
