package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterCostRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void aCostNeedsACategoryAnAmountAndADate() {
        assertThat(RecruiterCostRules.costProblem("ADVERTISING", new BigDecimal("1500.50"), TODAY, "LinkedIn", TODAY)).isNull();
        assertThat(RecruiterCostRules.costProblem("AGENCY_FEE", new BigDecimal("20000"), TODAY.minusDays(3), null, TODAY)).isNull();
        assertThat(RecruiterCostRules.costProblem("TRAVEL", BigDecimal.TEN, TODAY, null, TODAY)).contains("what the cost was for");
        assertThat(RecruiterCostRules.costProblem(null, BigDecimal.TEN, TODAY, null, TODAY)).contains("what the cost was for");
        assertThat(RecruiterCostRules.costProblem("OTHER", BigDecimal.ZERO, TODAY, null, TODAY)).contains("more than zero");
        assertThat(RecruiterCostRules.costProblem("OTHER", new BigDecimal("-5"), TODAY, null, TODAY)).contains("more than zero");
        assertThat(RecruiterCostRules.costProblem("OTHER", null, TODAY, null, TODAY)).contains("amount");
        assertThat(RecruiterCostRules.costProblem("OTHER", new BigDecimal("10.001"), TODAY, null, TODAY)).contains("two decimal");
        assertThat(RecruiterCostRules.costProblem("OTHER", new BigDecimal("10000001"), TODAY, null, TODAY)).contains("too large");
        assertThat(RecruiterCostRules.costProblem("OTHER", BigDecimal.TEN, null, null, TODAY)).contains("date");
        assertThat(RecruiterCostRules.costProblem("OTHER", BigDecimal.TEN, TODAY.plusDays(1), null, TODAY)).contains("future");
        assertThat(RecruiterCostRules.costProblem("OTHER", BigDecimal.TEN, TODAY.minusYears(6), null, TODAY)).contains("too far back");
        assertThat(RecruiterCostRules.costProblem("OTHER", BigDecimal.TEN, TODAY, "x".repeat(201), TODAY)).contains("note");
    }

    @Test
    void costPerHireIsLeftEmptyWithoutAHire() {
        assertThat(RecruiterCostRules.costPerHire(new BigDecimal("30000"), 3)).isEqualByComparingTo("10000.00");
        assertThat(RecruiterCostRules.costPerHire(new BigDecimal("100"), 3)).isEqualByComparingTo("33.33");
        assertThat(RecruiterCostRules.costPerHire(new BigDecimal("30000"), 0)).isNull();
        assertThat(RecruiterCostRules.costPerHire(null, 2)).isNull();
    }
}
