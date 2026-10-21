package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IncapacityRulesTest {

    private static final ErPolicy POLICY = ErPolicy.defaults();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final Set<String> ALL_PERFORMANCE = Set.of("STANDARD_SET", "SHORTFALL_ASSESSED", "SUPPORT_GIVEN", "TIME_TO_IMPROVE");
    private static final Set<String> ALL_HEALTH = Set.of("NATURE_INVESTIGATED", "MEDICAL_EVIDENCE", "ALTERNATIVES_CONSIDERED");

    @Test
    void eachKindHasItsOwnSteps() {
        assertThat(IncapacityRules.stepsFor("POOR_PERFORMANCE")).hasSize(4);
        assertThat(IncapacityRules.stepsFor("ILL_HEALTH")).hasSize(3);
        assertThat(IncapacityRules.stepsFor("OTHER")).isEmpty();
    }

    @Test
    void missingStepsAreListedInOrder() {
        assertThat(IncapacityRules.missing("POOR_PERFORMANCE", Set.of("STANDARD_SET", "SUPPORT_GIVEN")))
                .containsExactly("Performance was assessed against that standard", "A reasonable time to improve was allowed");
        assertThat(IncapacityRules.missing("ILL_HEALTH", ALL_HEALTH)).isEmpty();
    }

    @Test
    void aStepMustBelongToTheCaseAndBeDone() {
        assertThat(IncapacityRules.stepProblem("ILL_HEALTH", "MEDICAL_EVIDENCE", LocalDate.of(2026, 10, 1), TODAY)).isNull();
        assertThat(IncapacityRules.stepProblem("ILL_HEALTH", "STANDARD_SET", LocalDate.of(2026, 10, 1), TODAY)).contains("does not belong");
        assertThat(IncapacityRules.stepProblem("ILL_HEALTH", "MEDICAL_EVIDENCE", null, TODAY)).contains("date");
        assertThat(IncapacityRules.stepProblem("ILL_HEALTH", "MEDICAL_EVIDENCE", LocalDate.of(2026, 10, 21), TODAY)).contains("before it was done");
    }

    @Test
    void dismissalForIllHealthNeedsEveryStep() {
        assertThat(IncapacityRules.outcomeProblem("ILL_HEALTH", "DISMISSAL", Set.of("MEDICAL_EVIDENCE"), null, null, TODAY, POLICY, "Permanent"))
                .contains("Before a dismissal").contains("investigated").contains("another post");
        assertThat(IncapacityRules.outcomeProblem("ILL_HEALTH", "DISMISSAL", ALL_HEALTH, null, null, TODAY, POLICY, "Permanent incapacity, no suitable post")).isNull();
    }

    @Test
    void otherOutcomesNeedOnlyReasons() {
        assertThat(IncapacityRules.outcomeProblem("ILL_HEALTH", "ADJUSTED_DUTIES", Set.of(), null, null, TODAY, POLICY, "Lighter duties")).isNull();
        assertThat(IncapacityRules.outcomeProblem("POOR_PERFORMANCE", "SUPPORT_PLAN", Set.of(), null, null, TODAY, POLICY, "More coaching")).isNull();
        assertThat(IncapacityRules.outcomeProblem("ILL_HEALTH", "ADJUSTED_DUTIES", Set.of(), null, null, TODAY, POLICY, " ")).contains("required");
    }

    @Test
    void anOutcomeMustBelongToTheKindOfCase() {
        assertThat(IncapacityRules.outcomeProblem("ILL_HEALTH", "NO_ACTION", ALL_HEALTH, null, null, TODAY, POLICY, "x")).contains("does not belong");
        assertThat(IncapacityRules.outcomeProblem("POOR_PERFORMANCE", "RETURN_TO_WORK", ALL_PERFORMANCE, null, null, TODAY, POLICY, "x")).contains("does not belong");
    }

    @Test
    void dismissalForPoorPerformanceNeedsAFinishedReviewPeriodOfEnoughDays() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        assertThat(IncapacityRules.outcomeProblem("POOR_PERFORMANCE", "DISMISSAL", ALL_PERFORMANCE, null, null, TODAY, POLICY, "x")).contains("review period");
        assertThat(IncapacityRules.outcomeProblem("POOR_PERFORMANCE", "DISMISSAL", ALL_PERFORMANCE, from, LocalDate.of(2026, 10, 21), TODAY, POLICY, "x"))
                .contains("has not ended");
        assertThat(IncapacityRules.outcomeProblem("POOR_PERFORMANCE", "DISMISSAL", ALL_PERFORMANCE, from, LocalDate.of(2026, 8, 20), TODAY, POLICY, "x"))
                .contains("shorter than the company minimum of 28 days");
        assertThat(IncapacityRules.outcomeProblem("POOR_PERFORMANCE", "DISMISSAL", ALL_PERFORMANCE, from, LocalDate.of(2026, 8, 28), TODAY, POLICY, "Did not reach the standard"))
                .isNull();
    }

    @Test
    void theReviewPeriodCountsBothEndDays() {
        assertThat(IncapacityRules.reviewShort(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 28), POLICY)).isFalse();
        assertThat(IncapacityRules.reviewShort(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 27), POLICY)).isTrue();
        assertThat(IncapacityRules.reviewProblem(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 1))).contains("cannot end before");
        assertThat(IncapacityRules.reviewProblem(null, LocalDate.of(2026, 8, 1))).contains("start and end");
    }
}
