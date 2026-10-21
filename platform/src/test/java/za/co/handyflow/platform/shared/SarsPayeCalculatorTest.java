package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worked examples that do not come from the code under test. The "SARS table" cases are rows of SARS's own monthly tax
 * deduction tables (2026 and 2027 tax years, read 2026-10-21): the table gives whole rand of monthly tax for the annual
 * equivalent of the band, before the medical credit, so the calculator with no medical members must round to the same
 * figure. The other cases are worked by hand in the comments.
 */
class SarsPayeCalculatorTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static final StatutoryYearRules Y2027 = new StatutoryYearRules(2027,
            LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
            List.of(new Bracket(d("0"), d("245100"), d("0"), d("18")),
                    new Bracket(d("245100"), d("383100"), d("44118"), d("26")),
                    new Bracket(d("383100"), d("530200"), d("79998"), d("31")),
                    new Bracket(d("530200"), d("695800"), d("125599"), d("36")),
                    new Bracket(d("695800"), d("887000"), d("185215"), d("39")),
                    new Bracket(d("887000"), d("1878600"), d("259783"), d("41")),
                    new Bracket(d("1878600"), null, d("666339"), d("45"))),
            d("17820"), d("9765"), d("3249"), d("376"), d("376"), d("254"),
            d("17712"), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("430000"), d("0.275"), d("0.8"));

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

    private static final LocalDate UNDER_65 = LocalDate.of(1990, 6, 1);
    private static final LocalDate AGE_70 = LocalDate.of(1957, 6, 1);
    private static final LocalDate AGE_80 = LocalDate.of(1940, 6, 1);

    private static SarsPayrollResult calc(StatutoryYearRules rules, String salary, LocalDate dob, int members) {
        return SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(d(salary), null, null, null, dob, members, d("1000000")), rules);
    }

    private static long rand(BigDecimal v) { return v.setScale(0, RoundingMode.HALF_UP).longValueExact(); }

    // ── SARS monthly tables ────────────────────────────────────────────────────

    @Test
    void matchesSarsMonthlyTable2027() {
        // annual equivalent 180 648: R1 225 / R411 / R140
        assertThat(rand(calc(Y2027, "15054.00", UNDER_65, 0).payeAmount())).isEqualTo(1225L);
        assertThat(rand(calc(Y2027, "15054.00", AGE_70, 0).payeAmount())).isEqualTo(411L);
        assertThat(rand(calc(Y2027, "15054.00", AGE_80, 0).payeAmount())).isEqualTo(140L);
        // annual equivalent 360 036: R4 682 / R3 868 / R3 597
        assertThat(rand(calc(Y2027, "30003.00", UNDER_65, 0).payeAmount())).isEqualTo(4682L);
        assertThat(rand(calc(Y2027, "30003.00", AGE_70, 0).payeAmount())).isEqualTo(3868L);
        assertThat(rand(calc(Y2027, "30003.00", AGE_80, 0).payeAmount())).isEqualTo(3597L);
        // annual equivalent 720 624: R14 756 / R13 943 / R13 672
        assertThat(rand(calc(Y2027, "60052.00", UNDER_65, 0).payeAmount())).isEqualTo(14756L);
        assertThat(rand(calc(Y2027, "60052.00", AGE_70, 0).payeAmount())).isEqualTo(13943L);
        assertThat(rand(calc(Y2027, "60052.00", AGE_80, 0).payeAmount())).isEqualTo(13672L);
    }

    @Test
    void matchesSarsMonthlyTable2026() {
        // annual equivalent 180 348: R1 269 / R482 / R220
        assertThat(rand(calc(Y2026, "15029.00", UNDER_65, 0).payeAmount())).isEqualTo(1269L);
        assertThat(rand(calc(Y2026, "15029.00", AGE_70, 0).payeAmount())).isEqualTo(482L);
        assertThat(rand(calc(Y2026, "15029.00", AGE_80, 0).payeAmount())).isEqualTo(220L);
        // annual equivalent 359 724: R4 777 / R3 990 / R3 728
        assertThat(rand(calc(Y2026, "29977.00", UNDER_65, 0).payeAmount())).isEqualTo(4777L);
        assertThat(rand(calc(Y2026, "29977.00", AGE_70, 0).payeAmount())).isEqualTo(3990L);
        assertThat(rand(calc(Y2026, "29977.00", AGE_80, 0).payeAmount())).isEqualTo(3728L);
        // annual equivalent 719 688: R15 010 / R14 223 / R13 961
        assertThat(rand(calc(Y2026, "59974.00", UNDER_65, 0).payeAmount())).isEqualTo(15010L);
        assertThat(rand(calc(Y2026, "59974.00", AGE_70, 0).payeAmount())).isEqualTo(14223L);
        assertThat(rand(calc(Y2026, "59974.00", AGE_80, 0).payeAmount())).isEqualTo(13961L);
    }

    // ── Medical scheme fees tax credit ─────────────────────────────────────────

    @Test
    void noMedicalMembersMeansNoCredit() {
        // The old code gave every employee the credit. 30 003 a month, 2027: 74 001.36 - 17 820 = 56 181.36 / 12 = 4 681.78
        assertThat(calc(Y2027, "30003.00", UNDER_65, 0).payeAmount()).isEqualByComparingTo("4681.78");
        assertThat(calc(Y2027, "30003.00", UNDER_65, 0).medicalTaxCredit()).isEqualByComparingTo("0");
    }

    @Test
    void creditFollowsTheNumberOfPeopleCovered() {
        // 2027: R376 for the member, R376 for the first dependant, R254 for each further dependant
        assertThat(calc(Y2027, "30003.00", UNDER_65, 1).payeAmount()).isEqualByComparingTo("4305.78");   // 4 681.78 - 376
        assertThat(calc(Y2027, "30003.00", UNDER_65, 2).payeAmount()).isEqualByComparingTo("3929.78");   // - 752
        assertThat(calc(Y2027, "30003.00", UNDER_65, 3).payeAmount()).isEqualByComparingTo("3675.78");   // - 1 006
        assertThat(Y2027.monthlyMedicalCredit(4)).isEqualByComparingTo("1260");
        assertThat(Y2026.monthlyMedicalCredit(3)).isEqualByComparingTo("974");                          // 364 + 364 + 246
    }

    @Test
    void creditCannotTakeTaxBelowZero() {
        // 8 000 a month: 96 000 x 18% = 17 280, below the 17 820 rebate, so tax is nil with or without the credit
        assertThat(calc(Y2027, "8000.00", UNDER_65, 0).payeAmount()).isEqualByComparingTo("0");
        assertThat(calc(Y2027, "8000.00", UNDER_65, 4).payeAmount()).isEqualByComparingTo("0");
        // 20 000 a month: 240 000 x 18% = 43 200 - 17 820 = 25 380 / 12 = 2 115; two members take 752 off
        assertThat(calc(Y2027, "20000.00", UNDER_65, 2).payeAmount()).isEqualByComparingTo("1363.00");
    }

    // ── Age is taken at the end of the tax year ────────────────────────────────

    @Test
    void ageRebateUsesTheLastDayOfTheTaxYear() {
        LocalDate turns65On1March1961 = LocalDate.of(1961, 3, 1);
        // 2027 ends 28 Feb 2027: they are 65 then, so the secondary rebate applies to the whole year
        assertThat(calc(Y2027, "30003.00", turns65On1March1961, 0).secondaryRebate()).isEqualByComparingTo("9765");
        // 2026 ends 28 Feb 2026: still 64
        assertThat(calc(Y2026, "29977.00", turns65On1March1961, 0).secondaryRebate()).isEqualByComparingTo("0");
        assertThat(calc(Y2027, "30003.00", turns65On1March1961, 0).tertiaryRebate()).isEqualByComparingTo("0");
    }

    @Test
    void sameInputsGiveTheSameAnswerWhenRunAgain() {
        // no clock is used, so an old period recalculates exactly
        assertThat(calc(Y2026, "29977.00", AGE_70, 1)).isEqualTo(calc(Y2026, "29977.00", AGE_70, 1));
    }

    // ── Tax table edges ────────────────────────────────────────────────────────

    @Test
    void bracketEdgesAreContinuous() {
        assertThat(SarsPayeCalculator.tax(d("245100"), Y2027)).isEqualByComparingTo("44118.00");
        assertThat(SarsPayeCalculator.tax(d("245101"), Y2027)).isEqualByComparingTo("44118.26");
        assertThat(SarsPayeCalculator.tax(d("0"), Y2027)).isEqualByComparingTo("0");
        assertThat(SarsPayeCalculator.tax(d("2000000"), Y2027)).isEqualByComparingTo("720969.00");   // 666 339 + 45% of 121 400 (= 54 630)
    }

    // ── Retirement cap, UIF, SDL ───────────────────────────────────────────────

    @Test
    void retirementDeductionIsCappedPerYear() {
        // 200 000 a month = 2 400 000 a year; 27.5% is 660 000, so the rand cap binds
        var in = new SarsPayeCalculator.Inputs(d("200000"), null, d("50000"), null, UNDER_65, 0, d("5000000"));
        assertThat(SarsPayeCalculator.calculate(in, Y2027).taxableIncome()).isEqualByComparingTo("1970000");   // 2 400 000 - 430 000
        assertThat(SarsPayeCalculator.calculate(in, Y2026).taxableIncome()).isEqualByComparingTo("2050000");   // 2 400 000 - 350 000
    }

    @Test
    void retirementDeductionIsAlsoCappedAtAShareOfIncome() {
        // 10 000 a month = 120 000 a year; 27.5% = 33 000; pension 5 000 a month = 60 000 a year
        var in = new SarsPayeCalculator.Inputs(d("10000"), null, d("5000"), null, UNDER_65, 0, d("1000000"));
        assertThat(SarsPayeCalculator.calculate(in, Y2027).taxableIncome()).isEqualByComparingTo("87000");
    }

    @Test
    void onlyEightyPercentOfTravelAllowanceIsTaxed() {
        var in = new SarsPayeCalculator.Inputs(d("10000"), d("2000"), null, null, UNDER_65, 0, d("1000000"));
        assertThat(SarsPayeCalculator.calculate(in, Y2027).taxableIncome()).isEqualByComparingTo("139200");   // 120 000 + 2 000 x 80% x 12
    }

    @Test
    void uifStopsAtTheCeiling() {
        assertThat(calc(Y2027, "10000.00", UNDER_65, 0).uifEmployee()).isEqualByComparingTo("100.00");
        assertThat(calc(Y2027, "20000.00", UNDER_65, 0).uifEmployee()).isEqualByComparingTo("177.12");
        assertThat(calc(Y2027, "20000.00", UNDER_65, 0).uifEmployer()).isEqualByComparingTo("177.12");
    }

    @Test
    void sdlOnlyAboveTheThreshold() {
        var at = new SarsPayeCalculator.Inputs(d("10000"), null, null, null, UNDER_65, 0, d("500000.00"));
        var above = new SarsPayeCalculator.Inputs(d("10000"), null, null, null, UNDER_65, 0, d("500000.01"));
        assertThat(SarsPayeCalculator.calculate(at, Y2027).sdlAmount()).isEqualByComparingTo("0");
        assertThat(SarsPayeCalculator.calculate(above, Y2027).sdlAmount()).isEqualByComparingTo("100.00");
    }

    // ── Naming the tax year ────────────────────────────────────────────────────

    @Test
    void taxYearIsNamedByTheYearItEnds() {
        assertThat(StatutoryYearRules.taxYearOf(LocalDate.of(2026, 2, 28))).isEqualTo(2026);
        assertThat(StatutoryYearRules.taxYearOf(LocalDate.of(2026, 3, 1))).isEqualTo(2027);
        assertThat(StatutoryYearRules.taxYearOf(LocalDate.of(2025, 4, 30))).isEqualTo(2026);
        assertThat(StatutoryYearRules.taxYearOf(LocalDate.of(2026, 1, 15))).isEqualTo(2026);
        assertThat(StatutoryYearRules.taxYearOf(LocalDate.of(2026, 12, 31))).isEqualTo(2027);
    }
}
