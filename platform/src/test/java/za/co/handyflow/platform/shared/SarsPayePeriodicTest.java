package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Weekly and fortnightly pay under the periodic method. The expected figures were checked against SARS's published 2027 weekly
 * deduction table (annual equivalent = 52 x the band's midpoint; band R4 981 to R5 001 gives R578 tax, band R6 010 to R6 030 gives R845).
 */
class SarsPayePeriodicTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static final StatutoryYearRules Y2027 = new StatutoryYearRules(2027,
            LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
            List.of(new Bracket(d("0"), d("245100"), d("0"), d("18")),
                    new Bracket(d("245100"), d("383100"), d("44118"), d("26")),
                    new Bracket(d("383100"), null, d("79998"), d("31"))),
            d("17820"), d("9765"), d("3249"), d("376"), d("376"), d("254"),
            d("17712"), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("430000"), d("0.275"), d("0.8"));

    private static final LocalDate UNDER_65 = LocalDate.of(1990, 6, 1);

    private static SarsPayeCalculator.Inputs pay(String amount) {
        return new SarsPayeCalculator.Inputs(d(amount), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, UNDER_65, 0, d("300000"));
    }

    @Test
    void weeklyPayeMatchesTheSarsTableAtTheMidpointOfABand() {
        // 4 991 x 52 = 259 532; tax 44 118 + 26% of 14 432 = 47 870.32; less 17 820 rebate = 30 050.32; a 52nd = 577.89 (table: R578)
        var r = SarsPayeCalculator.calculate(pay("4991"), Y2027, 52, BigDecimal.ZERO);
        assertEquals(0, d("577.89").compareTo(r.payeAmount()));
    }

    @Test
    void weeklyPayeMatchesTheSarsTableInAHigherBand() {
        // 6 020 x 52 = 313 040; 44 118 + 26% of 67 940 = 61 782.40; less 17 820 = 43 962.40; a 52nd = 845.43 (table: R845)
        var r = SarsPayeCalculator.calculate(pay("6020"), Y2027, 52, BigDecimal.ZERO);
        assertEquals(0, d("845.43").compareTo(r.payeAmount()));
    }

    @Test
    void fortnightlyPayeAnnualisesByTwentySix() {
        // 9 000 x 26 = 234 000: 18% = 42 120, less 17 820 = 24 300; a 26th = 934.62
        var r = SarsPayeCalculator.calculate(pay("9000"), Y2027, 26, BigDecimal.ZERO);
        assertEquals(0, d("934.62").compareTo(r.payeAmount()));
    }

    @Test
    void twelvePeriodsGivesTheSameAnswerAsTheMonthlyMethod() {
        var in = new SarsPayeCalculator.Inputs(d("30000"), BigDecimal.ZERO, BigDecimal.ZERO, d("1500"), UNDER_65, 1, d("360000"));
        var monthly = SarsPayeCalculator.calculate(in, Y2027);
        var periodic = SarsPayeCalculator.calculate(in, Y2027, 12, BigDecimal.ZERO);
        assertEquals(0, monthly.payeAmount().compareTo(periodic.payeAmount()));
        assertEquals(0, monthly.uifEmployee().compareTo(periodic.uifEmployee()));
        assertEquals(0, d("4305.00").compareTo(periodic.payeAmount()));
    }

    @Test
    void theMedicalCreditIsSharedAcrossThePeriods() {
        // 4 991 weekly with the taxpayer's credit: annual tax 30 050.32 less 12 x 376 = 25 538.32; a 52nd = 491.12
        var in = new SarsPayeCalculator.Inputs(d("4991"), BigDecimal.ZERO, BigDecimal.ZERO, d("400"), UNDER_65, 1, d("300000"));
        var r = SarsPayeCalculator.calculate(in, Y2027, 52, BigDecimal.ZERO);
        assertEquals(0, d("491.12").compareTo(r.payeAmount()));
    }

    @Test
    void uifOnAWeeklyPayrollStopsAtTheMonthlyCeiling() {
        // R5 000 a week: the first three weeks of a month use 15 000 of the 17 712 ceiling; the fourth has 2 712 of room
        assertEquals(0, d("50.00").compareTo(SarsPayeCalculator.calculate(pay("5000"), Y2027, 52, BigDecimal.ZERO).uifEmployee()));
        assertEquals(0, d("50.00").compareTo(SarsPayeCalculator.calculate(pay("5000"), Y2027, 52, d("10000")).uifEmployee()));
        assertEquals(0, d("27.12").compareTo(SarsPayeCalculator.calculate(pay("5000"), Y2027, 52, d("15000")).uifEmployee()));
        assertEquals(0, d("0.00").compareTo(SarsPayeCalculator.calculate(pay("5000"), Y2027, 52, d("17712")).uifEmployee()));
    }

    @Test
    void uifBaseNeverGoesNegative() {
        assertEquals(0, BigDecimal.ZERO.compareTo(SarsPayeCalculator.uifBase(d("5000"), d("17712"), d("20000"))));
    }

    @Test
    void theMonthlyMethodIgnoresThePriorUifBase() {
        var r = SarsPayeCalculator.calculate(pay("20000"), Y2027, 12, d("17712"));
        assertEquals(0, d("177.12").compareTo(r.uifEmployee()));
    }
}
