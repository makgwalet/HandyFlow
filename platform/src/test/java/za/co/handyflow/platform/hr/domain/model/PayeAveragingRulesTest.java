package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The averaging method for regular pay. Figures are worked by hand from the 2026 tables below. */
class PayeAveragingRulesTest {

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

    private static BigDecimal paye(String monthly, String priorTaxable, String priorPaye, int months) {
        return PayeAveragingRules.paye(d(monthly), d(priorTaxable), d(priorPaye), months, d("17235"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Y2026);
    }

    @Test
    void steadyPayGivesTheSameEveryMonthAsThePeriodicMethod() {
        // R30 000 a month: R360 000 a year, tax 74 632 less the rebate 17 235 = 57 397, a month 4 783.08.
        assertThat(paye("30000", "0", "0", 1)).isEqualByComparingTo("4783.08");
        // The year's liability is worked on the whole year to date, so a cent moves between months and the total stays exact.
        assertThat(paye("30000", "30000", "4783.08", 2)).isEqualByComparingTo("4783.09");
        assertThat(paye("30000", "60000", "9566.17", 3)).isEqualByComparingTo("4783.08");
    }

    @Test
    void aBiggerMonthIsTaxedOnTheYearSoFarNotAsIfEveryMonthWereThatBig() {
        // Two months at 30 000 then 60 000: year to date 120 000 over 3 months is 480 000 a year; tax 111 307 less 17 235 = 94 072;
        // for 3 months 23 518.00; 9 566.17 already paid. The periodic method would take 15 020.17 on 720 000 a year.
        assertThat(paye("60000", "60000", "9566.17", 3)).isEqualByComparingTo("13951.83");
    }

    @Test
    void aMonthCannotGoBelowNilAndTheOverpaymentIsCaughtUpLater() {
        // 60 000 in month 1 pays 15 020.17. Month 2 with no pay: year to date 60 000 over 2 months is 360 000 a year, 9 566.17 for 2 months: already over.
        assertThat(paye("60000", "0", "0", 1)).isEqualByComparingTo("15020.17");
        assertThat(paye("0", "60000", "15020.17", 2)).isEqualByComparingTo("0.00");
        // Month 3 at 30 000: 90 000 over 3 months is 360 000 a year, 14 349.25 for 3 months; 15 020.17 already paid: still nothing due.
        assertThat(paye("30000", "60000", "15020.17", 3)).isEqualByComparingTo("0.00");
    }

    @Test
    void underTheThresholdNothingIsDue() {
        assertThat(paye("7000", "7000", "0", 2)).isEqualByComparingTo("0.00");
    }

    @Test
    void monthsRunFromTheStartOfTheTaxYearOrThePersonsFirstMonth() {
        LocalDate yearStart = LocalDate.of(2025, 3, 1);
        assertThat(PayeAveragingRules.monthsElapsed(yearStart, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 3, 31))).isEqualTo(1);
        assertThat(PayeAveragingRules.monthsElapsed(yearStart, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 10, 31))).isEqualTo(8);
        assertThat(PayeAveragingRules.monthsElapsed(yearStart, LocalDate.of(2025, 8, 15), LocalDate.of(2025, 10, 31))).isEqualTo(3);
        assertThat(PayeAveragingRules.monthsElapsed(yearStart, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 2, 28))).isEqualTo(12);
        assertThat(PayeAveragingRules.monthsElapsed(yearStart, LocalDate.of(2026, 1, 1), LocalDate.of(2025, 10, 31))).isEqualTo(1);
        assertThat(PayeAveragingRules.monthsElapsed(yearStart, null, LocalDate.of(2025, 4, 30))).isEqualTo(2);
    }

    @Test
    void onlyTwoMethodsAreValid() {
        assertThat(PayeAveragingRules.valid("PERIODIC")).isTrue();
        assertThat(PayeAveragingRules.valid("AVERAGING")).isTrue();
        assertThat(PayeAveragingRules.valid("cumulative")).isFalse();
        assertThat(PayeAveragingRules.valid(null)).isFalse();
    }
}
