package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingCostRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static String problem(String cat, String amount, LocalDate on, String note) {
        return TrainingCostRules.costProblem(cat, amount == null ? null : new BigDecimal(amount), on, note, TODAY);
    }

    @Test
    void aGoodCostHasNoProblem() {
        assertThat(problem("VENUE", "1500.50", TODAY, "Boardroom hire")).isNull();
        assertThat(problem("TRAINER_FEE", "10000000", TODAY.minusDays(3), null)).isNull();
    }

    @Test
    void badCostsAreRefused() {
        assertThat(problem(null, "100", TODAY, null)).contains("what the cost was for");
        assertThat(problem("LUNCH", "100", TODAY, null)).contains("what the cost was for");
        assertThat(problem("VENUE", null, TODAY, null)).contains("more than zero");
        assertThat(problem("VENUE", "0", TODAY, null)).contains("more than zero");
        assertThat(problem("VENUE", "-5", TODAY, null)).contains("more than zero");
        assertThat(problem("VENUE", "10.123", TODAY, null)).contains("two decimal");
        assertThat(problem("VENUE", "10000000.01", TODAY, null)).contains("too large");
        assertThat(problem("VENUE", "100", null, null)).contains("date");
        assertThat(problem("VENUE", "100", TODAY.plusDays(1), null)).contains("future");
        assertThat(problem("VENUE", "100", TODAY.minusYears(6), null)).contains("too far back");
        assertThat(problem("VENUE", "100", TODAY, "x".repeat(201))).contains("too long");
    }

    @Test
    void costPerPersonIsRoundedToTheCent() {
        assertThat(TrainingCostRules.costPerPerson(new BigDecimal("1000"), 3)).isEqualByComparingTo("333.33");
        assertThat(TrainingCostRules.costPerPerson(new BigDecimal("1000"), 0)).isNull();
        assertThat(TrainingCostRules.costPerPerson(null, 4)).isNull();
    }

    @Test
    void passMarkRules() {
        assertThat(TrainingCostRules.passMarkProblem(null)).isNull();
        assertThat(TrainingCostRules.passMarkProblem(new BigDecimal("0"))).isNull();
        assertThat(TrainingCostRules.passMarkProblem(new BigDecimal("75.5"))).isNull();
        assertThat(TrainingCostRules.passMarkProblem(new BigDecimal("100"))).isNull();
        assertThat(TrainingCostRules.passMarkProblem(new BigDecimal("-1"))).contains("between 0 and 100");
        assertThat(TrainingCostRules.passMarkProblem(new BigDecimal("100.5"))).contains("between 0 and 100");
        assertThat(TrainingCostRules.passMarkProblem(new BigDecimal("75.123"))).contains("two decimal");
    }

    @Test
    void theScoreDecidesWhenThereIsAPassMark() {
        BigDecimal mark = new BigDecimal("70");
        assertThat(TrainingCostRules.decide(new BigDecimal("70"), mark, false).passed()).isTrue();
        assertThat(TrainingCostRules.decide(new BigDecimal("69.99"), mark, true).passed()).isFalse();
        assertThat(TrainingCostRules.decide(null, mark, true).problem()).contains("pass mark of 70%");
    }

    @Test
    void withoutAPassMarkThePersonDecides() {
        assertThat(TrainingCostRules.decide(null, null, true).passed()).isTrue();
        assertThat(TrainingCostRules.decide(new BigDecimal("10"), null, true).passed()).isTrue();
        assertThat(TrainingCostRules.decide(new BigDecimal("99"), null, false).passed()).isFalse();
        assertThat(TrainingCostRules.decide(null, null, false).problem()).isNull();
    }
}
