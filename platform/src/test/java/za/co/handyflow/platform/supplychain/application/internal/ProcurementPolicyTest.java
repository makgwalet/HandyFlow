package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.ProcurementPolicy.Policy;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementPolicyTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static Policy p(String basis, String recoverable, boolean includeNon) {
        return new Policy(basis, d(recoverable), includeNon, d("5"));
    }

    @Test
    void exVatByDefaultCountsOnlyTheNetAmount_theR100kExample() {
        Policy fullyRecoverable = p("EX_VAT", "100", true);
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), fullyRecoverable)).isEqualByComparingTo("100000.00");
    }

    @Test
    void aBusinessThatCannotRecoverVatBudgetsTheFullAmount() {
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), p("EX_VAT", "0", true))).isEqualByComparingTo("115000.00");
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), p("NET_OF_RECOVERABLE", "0", false))).isEqualByComparingTo("115000.00");
    }

    @Test
    void partialRecoveryAddsOnlyTheUnrecoverableShare() {
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), p("EX_VAT", "60", true))).isEqualByComparingTo("106000.00");
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), p("NET_OF_RECOVERABLE", "60", true))).isEqualByComparingTo("106000.00");
    }

    @Test
    void unticking_includeNonRecoverable_makesExVatPureNet() {
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), p("EX_VAT", "0", false))).isEqualByComparingTo("100000.00");
    }

    @Test
    void incVatCountsTheWholeInvoice() {
        assertThat(ProcurementPolicy.budgetAmount(d("100000"), d("15000"), p("INC_VAT", "100", true))).isEqualByComparingTo("115000.00");
    }

    @Test
    void validation() {
        assertThat(ProcurementPolicy.problem(p("EX_VAT", "100", true))).isNull();
        assertThat(ProcurementPolicy.problem(p("GROSS", "100", true))).contains("basis");
        assertThat(ProcurementPolicy.problem(p("EX_VAT", "101", true))).contains("Recoverable");
        assertThat(ProcurementPolicy.problem(new Policy("EX_VAT", d("100"), true, d("-1")))).contains("tolerance");
        assertThat(ProcurementPolicy.problem(null)).isNotNull();
    }

    @Test
    void baseCurrencyDefaultsToRandAndIsUpperCased() {
        assertThat(new Policy("EX_VAT", d("100"), true, d("5")).baseCurrency()).isEqualTo("ZAR");
        assertThat(new Policy("EX_VAT", d("100"), true, d("5"), " usd ").baseCurrency()).isEqualTo("USD");
        assertThat(new Policy("EX_VAT", d("100"), true, d("5"), null).baseCurrency()).isEqualTo("ZAR");
    }

    @Test
    void rejectsAnInvalidBaseCurrency() {
        assertThat(ProcurementPolicy.problem(new Policy("EX_VAT", d("100"), true, d("5"), "RAND"))).contains("Base currency");
        assertThat(ProcurementPolicy.problem(new Policy("EX_VAT", d("100"), true, d("5"), "EUR"))).isNull();
    }

    @Test
    void vatFactorMatchesTheSharedRule() {
        assertThat(ProcurementPolicy.vatFactor(p("NET_OF_RECOVERABLE", "60", true))).isEqualByComparingTo("0.4");
    }
}
