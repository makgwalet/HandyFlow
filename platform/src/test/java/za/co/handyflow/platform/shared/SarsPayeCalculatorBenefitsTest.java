package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Employer-paid medical and retirement contributions and other fringe benefits. The figures are worked by hand on the 2026 brackets:
 * R30 000 a month is R360 000 a year; the second bracket starts at R237 100 (R42 678 plus 26%) and the third at R370 500 (R77 362 plus 31%).
 */
class SarsPayeCalculatorBenefitsTest {

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

    private static final LocalDate YOUNG = LocalDate.of(1990, 6, 1);

    private static SarsPayrollResult calc(String pension, String empMedical, String empRetirement, String fringe) {
        return SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(d("30000"), null, d(pension), null, YOUNG, 0, d("1000000"),
                d(empMedical), d(empRetirement), d(fringe)), Y2026);
    }

    @Test
    void anEmployerMedicalContributionIsTaxedAsIncome() {
        SarsPayrollResult without = calc("0", "0", "0", "0");
        SarsPayrollResult with = calc("0", "3000", "0", "0");

        assertThat(without.taxableIncome().compareTo(d("360000"))).isZero();
        assertThat(with.taxableIncome().compareTo(d("396000"))).isZero();
        // 85 267 - 74 632 = 10 635 a year
        assertThat(with.taxBeforeRebate().subtract(without.taxBeforeRebate()).compareTo(d("10635"))).isZero();
        assertThat(with.payeAmount().subtract(without.payeAmount()).compareTo(d("886.25"))).isZero();
    }

    @Test
    void anotherFringeBenefitIsTaxedTheSameWay() {
        assertThat(calc("0", "0", "0", "1000").taxableIncome().compareTo(d("372000"))).isZero();
    }

    @Test
    void employerRetirementIsTaxedThenDeductedWithTheEmployeesOwn() {
        // income 360 000 + 36 000 employer = 396 000; retirement (2 000 + 3 000) x 12 = 60 000, below the cap of 27.5% of 396 000
        SarsPayrollResult r = calc("2000", "0", "3000", "0");

        assertThat(r.taxableIncome().compareTo(d("336000"))).isZero();
        assertThat(r.taxBeforeRebate().compareTo(d("68392"))).isZero();
    }

    @Test
    void theRetirementCapAppliesToEmployeeAndEmployerTogether() {
        // employee 10 000 + employer 10 000 = 240 000 a year; remuneration 360 000 + 120 000 = 480 000; cap 27.5% = 132 000
        SarsPayrollResult r = calc("10000", "0", "10000", "0");

        assertThat(r.taxableIncome().compareTo(d("348000"))).isZero();
    }

    @Test
    void withNoBenefitsTheOldAndNewConstructorsAgree() {
        SarsPayrollResult old = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(d("30000"), null, d("2000"), null, YOUNG, 1, d("1000000")), Y2026);
        SarsPayrollResult now = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(d("30000"), null, d("2000"), null, YOUNG, 1, d("1000000"),
                null, null, null), Y2026);

        assertThat(now.payeAmount().compareTo(old.payeAmount())).isZero();
        assertThat(now.taxableIncome().compareTo(old.taxableIncome())).isZero();
    }

    @Test
    void benefitsDoNotChangeUifOrSdl() {
        SarsPayrollResult without = calc("0", "0", "0", "0");
        SarsPayrollResult with = calc("0", "3000", "3000", "1000");

        assertThat(with.uifEmployee().compareTo(without.uifEmployee())).isZero();
        assertThat(with.sdlAmount().compareTo(without.sdlAmount())).isZero();
    }
}
