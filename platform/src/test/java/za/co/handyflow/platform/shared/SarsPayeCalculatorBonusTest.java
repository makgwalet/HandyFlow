package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bonus or back pay is taxed as an annual payment: the extra tax the year bears once it is added to the annualised month, all of it in the
 * month it is paid. Figures are worked by hand on the 2026 brackets: R30 000 a month is R360 000 a year (tax R74 632, rebate R17 235); the
 * third bracket starts at R370 500 (R77 362 plus 31%).
 */
class SarsPayeCalculatorBonusTest {

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

    private static SarsPayrollResult calc(String salary, String pension, int medical, String irregular) {
        return SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(d(salary), null, d(pension), null, YOUNG, medical, d("1000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, d(irregular)), Y2026);
    }

    private static void is(String expected, BigDecimal actual, String what) {
        assertTrue(actual.compareTo(d(expected)) == 0, what + ": expected " + expected + " but was " + actual);
    }

    @Test
    void withoutABonusTheResultIsExactlyWhatItAlwaysWas() {
        SarsPayrollResult plain = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(d("30000"), null, d("0"), null, YOUNG, 0, d("1000000")), Y2026);
        SarsPayrollResult zero = calc("30000", "0", 0, "0");
        is("4783.08", plain.payeAmount(), "paye");           // (74 632 - 17 235) / 12 = 4 783.08
        is(plain.payeAmount().toPlainString(), zero.payeAmount(), "paye with a zero irregular");
        is(plain.taxableIncome().toPlainString(), zero.taxableIncome(), "taxable");
    }

    @Test
    void aBonusIsTaxedByTheExtraTheYearBearsAndAllOfItFallsInTheMonth() {
        SarsPayrollResult r = calc("30000", "0", 0, "30000");
        // 360 000 + 30 000 = 390 000: 77 362 + 31% x 19 500 = 83 407; less 17 235 = 66 172. Without it 57 397. The extra is 8 775.00
        // (10 500 at 26% and 19 500 at 31%), added to the month's own 4 783.08.
        is("13558.08", r.payeAmount(), "paye");
        is("390000", r.taxableIncome(), "taxable income");
        is("83407", r.taxBeforeRebate(), "tax before rebate");
    }

    @Test
    void aBonusIsRemunerationForUifUpToTheCeilingAndForSdl() {
        SarsPayrollResult small = calc("10000", "0", 0, "20000");
        is("177.12", small.uifEmployee(), "UIF stops at the ceiling of 17 712 (10 000 alone gives 100.00)");
        is("300.00", small.sdlAmount(), "SDL is 1% of 10 000 + 20 000");
        is("100.00", calc("10000", "0", 0, "0").uifEmployee(), "UIF without a bonus");
    }

    @Test
    void anEmployeeWhoseYearStaysUnderTheRebatePaysNoTaxOnTheBonus() {
        // R5 000 a month is R60 000 a year (tax 10 800), and R10 000 more makes 70 000 (12 600): both under the R17 235 rebate.
        SarsPayrollResult r = calc("5000", "0", 0, "10000");
        is("0", r.payeAmount(), "paye");
    }

    @Test
    void aSmallEarnerPaysEighteenPercentOfTheBonusAboveTheRebate() {
        // R10 000 a month: 120 000 gives 21 600 - 17 235 = 4 365 (363.75 a month); 125 000 gives 22 500 - 17 235 = 5 265. The extra is 900.00.
        is("1263.75", calc("10000", "0", 0, "5000").payeAmount(), "paye");
    }

    @Test
    void theMedicalCreditDoesNotChangeTheTaxOnTheBonus() {
        SarsPayrollResult without = calc("30000", "0", 0, "30000");
        SarsPayrollResult with = calc("30000", "0", 1, "30000");
        // R364 a month credit lowers the month's own tax by 364.00 and leaves the 8 775.00 on the bonus alone
        is("364.00", without.payeAmount().subtract(with.payeAmount()), "credit");
        is("13194.08", with.payeAmount(), "paye with credit");
    }

    @Test
    void theRetirementDeductionStillAppliesAndIsCappedOnTheRemunerationIncludingTheBonus() {
        // pension 5 000 x 12 = 60 000 is well under 27.5% of 360 000 and of 390 000
        SarsPayrollResult r = calc("30000", "5000", 0, "30000");
        // 390 000 - 60 000 = 330 000: 42 678 + 26% x 92 900 = 66 832, less 17 235 = 49 597. Without the bonus: 300 000 gives 59 032 - 17 235 = 41 797.
        // The extra is 7 800.00 (26% of 30 000); the month's own tax is 41 797 / 12 = 3 483.08.
        is("11283.08", r.payeAmount(), "paye");
        is("330000", r.taxableIncome(), "taxable income");
    }

    @Test
    void theRetirementCapRisesWithABonusSoMoreOfTheContributionIsAllowed() {
        // pension 9 000 x 12 = 108 000. Cap on 360 000 is 99 000, so 99 000 is allowed; on 390 000 it is 107 250.
        SarsPayrollResult withBonus = calc("30000", "9000", 0, "30000");
        is("282750", withBonus.taxableIncome(), "390 000 less the capped 107 250");
    }
}
