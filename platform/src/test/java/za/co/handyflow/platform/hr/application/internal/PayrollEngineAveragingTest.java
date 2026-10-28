package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The engine with and without a year to date: same answer for steady pay, lower for a big month, annual payments untouched. */
class PayrollEngineAveragingTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static final StatutoryYearRules Y2026 = new StatutoryYearRules(2026,
            LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28),
            List.of(new Bracket(d("0"), d("237100"), d("0"), d("18")),
                    new Bracket(d("237100"), d("370500"), d("42678"), d("26")),
                    new Bracket(d("370500"), d("512800"), d("77362"), d("31")),
                    new Bracket(d("512800"), d("673000"), d("121475"), d("36")),
                    new Bracket(d("673000"), d("857900"), d("179147"), d("39")),
                    new Bracket(d("857900"), d("1817000"), d("251258"), d("41")),
                    new Bracket(d("1817000"), null, d("644489"), d("45"))),
            d("17235"), d("9444"), d("3145"), d("364"), d("364"), d("246"),
            d("17712"), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("350000"), d("0.275"), d("0.8"));

    private final PayrollEngine engine = new PayrollEngine();
    private final HrEmployee jane = HrEmployee.create(TenantId.of(UUID.randomUUID()), "EMP-1", "Jane", "Dlamini",
            LocalDate.of(2020, 1, 1), "PERMANENT", d("30000"), "MONTHLY");

    private PayrollEngine.Period month(String basic, String bonus) {
        return new PayrollEngine.Period(d(basic), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, d(bonus), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void withoutAYearToDateTheResultIsTheOldOne() {
        PayrollEngine.Calc c = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), null);
        assertThat(c.result().payeAmount()).isEqualByComparingTo(c.periodicPaye());
        assertThat(c.regularTaxable()).isEqualByComparingTo("30000.00");
    }

    @Test
    void steadyPayAveragesToTheSameAsPeriodic() {
        PayrollEngine.Calc periodic = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), null);
        BigDecimal each = periodic.periodicPaye();
        PayrollEngine.Prior prior = new PayrollEngine.Prior(d("60000"), each.multiply(d("2")), 3);
        PayrollEngine.Calc avg = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), prior);
        assertThat(avg.regularPaye().subtract(each).abs().compareTo(d("0.02")) <= 0).isTrue();
    }

    @Test
    void aBonusIsTaxedTheSameWayEitherMethod() {
        PayrollEngine.Prior prior = new PayrollEngine.Prior(d("60000"), d("9566.17"), 3);
        PayrollEngine.Calc without = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), prior);
        PayrollEngine.Calc with = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "20000"), prior);
        PayrollEngine.Calc periodicWith = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "20000"), null);
        PayrollEngine.Calc periodicWithout = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), null);
        BigDecimal bonusTax = periodicWith.result().payeAmount().subtract(periodicWithout.result().payeAmount());
        assertThat(with.result().payeAmount().subtract(without.result().payeAmount())).isEqualByComparingTo(bonusTax);
        assertThat(with.regularPaye()).isEqualByComparingTo(without.regularPaye());
    }

    @Test
    void theMonthsOtherFiguresDoNotChange() {
        PayrollEngine.Prior prior = new PayrollEngine.Prior(d("60000"), d("9566.17"), 3);
        PayrollEngine.Calc a = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), prior);
        PayrollEngine.Calc p = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), null);
        assertThat(a.result().uifEmployee()).isEqualByComparingTo(p.result().uifEmployee());
        assertThat(a.result().grossSalary()).isEqualByComparingTo(p.result().grossSalary());
        assertThat(a.result().taxableIncome()).isEqualByComparingTo(p.result().taxableIncome());
    }

    @Test
    void aSecondRunInTheSameMonthDoesNotTaxTheSameMonthTwice() {
        BigDecimal each = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), null).periodicPaye();
        // Two earlier months and this month's first run are in the year to date; months is still 3 because it counts calendar months.
        PayrollEngine.Prior prior = new PayrollEngine.Prior(d("90000"), each.multiply(d("3")), 3);
        PayrollEngine.Calc second = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("0", "0"), prior);
        assertThat(second.regularPaye().compareTo(d("0.05")) <= 0).isTrue();
    }

    @Test
    void aSecondRunWithABonusOnlyAddsTheBonusTax() {
        BigDecimal each = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("30000", "0"), null).periodicPaye();
        PayrollEngine.Prior prior = new PayrollEngine.Prior(d("90000"), each.multiply(d("3")), 3);
        PayrollEngine.Calc empty = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("0", "0"), prior);
        PayrollEngine.Calc bonus = engine.calculate(jane, Y2026, d("5000000"), 1, BigDecimal.ZERO, month("0", "20000"), prior);
        assertThat(bonus.regularPaye()).isEqualByComparingTo(empty.regularPaye());
        assertThat(bonus.result().payeAmount().compareTo(empty.result().payeAmount()) > 0).isTrue();
    }
}
