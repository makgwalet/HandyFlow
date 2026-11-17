package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.BudgetRules;
import za.co.handyflow.platform.supplychain.application.internal.BudgetRules.State;
import za.co.handyflow.platform.supplychain.application.internal.BudgetRules.View;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetControlMode;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;
import za.co.handyflow.platform.supplychain.domain.model.ScBudget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static final BigDecimal NEAR = bd("90");

    private static View budget(BudgetScopeType t, String value, String amount, BudgetControlMode mode, String used) {
        return new View(UUID.randomUUID(), "FY27 " + value, t, value, bd(amount), mode, bd(used));
    }

    @Test
    void fits_isOk() {
        var r = BudgetRules.evaluate(List.of(budget(BudgetScopeType.DEPARTMENT, "Ops", "500000", BudgetControlMode.HARD_STOP, "180000")), bd("100000"), NEAR);
        assertThat(r.hasWarnings()).isFalse();
        assertThat(r.hardStop()).isFalse();
        assertThat(r.findings().get(0).state()).isEqualTo(State.OK);
        assertThat(r.findings().get(0).availableAfter()).isEqualByComparingTo("220000");
    }

    @Test
    void exactlyAtLimit_isNotExceeded_butNearLimit() {
        var r = BudgetRules.evaluate(List.of(budget(BudgetScopeType.DEPARTMENT, "Ops", "1000", BudgetControlMode.HARD_STOP, "400")), bd("600"), NEAR);
        assertThat(r.findings().get(0).state()).isEqualTo(State.NEAR_LIMIT);
        assertThat(r.hardStop()).isFalse();
    }

    @Test
    void overLimit_hardStopBlocks_warnOnlyFlags() {
        var hard = BudgetRules.evaluate(List.of(budget(BudgetScopeType.PROJECT, "P1", "1000", BudgetControlMode.HARD_STOP, "900")), bd("200"), NEAR);
        assertThat(hard.hardStop()).isTrue();
        assertThat(hard.message()).contains("exceeds").contains("by R 100.00");

        var warn = BudgetRules.evaluate(List.of(budget(BudgetScopeType.PROJECT, "P1", "1000", BudgetControlMode.WARN, "900")), bd("200"), NEAR);
        assertThat(warn.hardStop()).isFalse();
        assertThat(warn.hasWarnings()).isTrue();
    }

    @Test
    void noneMode_isNeverEvaluated() {
        var r = BudgetRules.evaluate(List.of(budget(BudgetScopeType.DEPARTMENT, "Ops", "100", BudgetControlMode.NONE, "500")), bd("500"), NEAR);
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void everyApplicableBudgetMustHaveRoom() {
        var roomy = budget(BudgetScopeType.DEPARTMENT, "Ops", "100000", BudgetControlMode.HARD_STOP, "0");
        var tight = budget(BudgetScopeType.PROJECT, "P1", "1000", BudgetControlMode.HARD_STOP, "950");
        var r = BudgetRules.evaluate(List.of(roomy, tight), bd("100"), NEAR);
        assertThat(r.hardStop()).isTrue();                       // the project budget alone blocks
        assertThat(r.warnings()).hasSize(1);
    }

    @Test
    void matching_isCaseInsensitiveAndDimensionSpecific() {
        assertThat(BudgetRules.matches(BudgetScopeType.DEPARTMENT, " operations ", "Operations", null, null)).isTrue();
        assertThat(BudgetRules.matches(BudgetScopeType.COST_CENTRE, "CC-10", "Operations", "cc-10", null)).isTrue();
        assertThat(BudgetRules.matches(BudgetScopeType.PROJECT, "P1", "P1", null, null)).isFalse();   // value is in the wrong dimension
        assertThat(BudgetRules.matches(BudgetScopeType.PROJECT, "P1", null, null, null)).isFalse();
    }

    @Test
    void closedBudgetCannotBeChanged() {
        ScBudget b = ScBudget.create(UUID.randomUUID(), "FY27 Ops", BudgetScopeType.DEPARTMENT, "Ops",
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), bd("500000"), BudgetControlMode.WARN, "ZAR", UUID.randomUUID());
        b.update("FY27 Operations", bd("600000"), BudgetControlMode.HARD_STOP);
        assertThat(b.getAmount()).isEqualByComparingTo("600000");
        b.close();
        assertThatThrownBy(() -> b.update(null, bd("1"), null)).isInstanceOf(IllegalStateException.class);
    }
}
