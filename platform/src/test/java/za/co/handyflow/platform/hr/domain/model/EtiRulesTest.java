package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.EtiRules.Result;
import za.co.handyflow.platform.hr.domain.model.EtiRules.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The 1 April 2025 table: R1 500 for pay of R2 500 to R5 499.99, tapering to nil at R7 500; half that in the second 12 months. */
class EtiRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final Table T = new Table(d("2500"), d("7500"), d("1500"), d("750"), d("5500"), d("0.75"), d("0.375"));
    private static final LocalDate END = LocalDate.of(2026, 10, 31);
    private static final LocalDate DOB_25 = LocalDate.of(2001, 3, 4);

    private Result calc(String pay, int prior) { return EtiRules.calculate(T, DOB_25, END, true, null, prior, d(pay), BigDecimal.ONE); }

    @Test
    void theFlatBandPaysTheMaximum() {
        assertThat(calc("2500", 0).amount()).isEqualByComparingTo("1500.00");
        assertThat(calc("5000", 0).amount()).isEqualByComparingTo("1500.00");
        assertThat(calc("5500", 0).amount()).isEqualByComparingTo("1500.00");
        assertThat(calc("5000", 0).month()).isEqualTo(1);
    }

    @Test
    void theTaperReducesByThreeQuartersOfPayAboveR5500() {
        assertThat(calc("6500", 0).amount()).isEqualByComparingTo("750.00");   // 1500 - 0.75 x 1000
        assertThat(calc("7000", 0).amount()).isEqualByComparingTo("375.00");
        assertThat(calc("7499.99", 0).amount()).isEqualByComparingTo("0.01");
    }

    @Test
    void thereIsNothingFromTheCapUpOrBelowTheMinimum() {
        assertThat(calc("7500", 0).qualifies()).isFalse();
        assertThat(calc("7500", 0).why()).contains("cap");
        assertThat(calc("2499.99", 0).qualifies()).isFalse();
        assertThat(calc("2499.99", 0).why()).contains("minimum");
    }

    @Test
    void theSecondTwelveMonthsAreHalfAndTheTwentyFifthIsNothing() {
        assertThat(calc("5000", 11).month()).isEqualTo(12);
        assertThat(calc("5000", 11).amount()).isEqualByComparingTo("1500.00");
        assertThat(calc("5000", 12).month()).isEqualTo(13);
        assertThat(calc("5000", 12).amount()).isEqualByComparingTo("750.00");
        assertThat(calc("6500", 12).amount()).isEqualByComparingTo("375.00");   // 750 - 0.375 x 1000
        assertThat(calc("5000", 23).month()).isEqualTo(24);
        assertThat(calc("5000", 24).qualifies()).isFalse();
        assertThat(calc("5000", 24).why()).contains("24 months");
    }

    @Test
    void aPartMonthIsGrossedUpToFindTheBandAndTheAmountScaledBack() {
        // Half the month: paid 2 500, which is 5 000 for a full month, so 1 500 x 0.5 = 750.
        Result r = EtiRules.calculate(T, DOB_25, END, true, null, 0, d("2500"), d("0.5"));
        assertThat(r.amount()).isEqualByComparingTo("750.00");
        // Half the month paid 6 000 is 12 000 a full month: over the cap.
        assertThat(EtiRules.calculate(T, DOB_25, END, true, null, 0, d("6000"), d("0.5")).qualifies()).isFalse();
        // Half the month paid 1 000 is 2 000 a full month: under the minimum.
        assertThat(EtiRules.calculate(T, DOB_25, END, true, null, 0, d("1000"), d("0.5")).qualifies()).isFalse();
    }

    @Test
    void ageIsTakenAtTheEndOfTheMonth() {
        assertThat(EtiRules.calculate(T, LocalDate.of(2008, 11, 1), END, true, null, 0, d("5000"), BigDecimal.ONE).qualifies()).isFalse();   // 17
        assertThat(EtiRules.calculate(T, LocalDate.of(2008, 10, 31), END, true, null, 0, d("5000"), BigDecimal.ONE).qualifies()).isTrue();    // 18 on the last day
        assertThat(EtiRules.calculate(T, LocalDate.of(1997, 11, 1), END, true, null, 0, d("5000"), BigDecimal.ONE).qualifies()).isTrue();     // 28... 29 next day
        assertThat(EtiRules.calculate(T, LocalDate.of(1996, 10, 31), END, true, null, 0, d("5000"), BigDecimal.ONE).qualifies()).isFalse();   // 30
        assertThat(EtiRules.calculate(T, LocalDate.of(1996, 11, 1), END, true, null, 0, d("5000"), BigDecimal.ONE).qualifies()).isTrue();     // 29
    }

    @Test
    void anIdAnExclusionAndMissingDetailsStopIt() {
        assertThat(EtiRules.calculate(T, DOB_25, END, false, null, 0, d("5000"), BigDecimal.ONE).why()).contains("ID");
        assertThat(EtiRules.calculate(T, DOB_25, END, true, "Domestic worker", 0, d("5000"), BigDecimal.ONE).why()).contains("Domestic worker");
        assertThat(EtiRules.calculate(T, null, END, true, null, 0, d("5000"), BigDecimal.ONE).why()).contains("date of birth");
        assertThat(EtiRules.calculate(T, DOB_25, END, true, null, 0, BigDecimal.ZERO, BigDecimal.ONE).qualifies()).isFalse();
    }

    @Test
    void theShareOfAMonthPaidAndTheSplitAgainstPaye() {
        assertThat(EtiRules.paidShare(d("11"), 22)).isEqualByComparingTo("0.5");
        assertThat(EtiRules.paidShare(d("23"), 22)).isEqualByComparingTo("1");
        assertThat(EtiRules.paidShare(null, 22)).isEqualByComparingTo("1");
        BigDecimal[] a = EtiRules.split(d("3000"), d("10000"));
        assertThat(a[0]).isEqualByComparingTo("3000.00");
        assertThat(a[1]).isEqualByComparingTo("0.00");
        BigDecimal[] b = EtiRules.split(d("3000"), d("1200"));
        assertThat(b[0]).isEqualByComparingTo("1200.00");
        assertThat(b[1]).isEqualByComparingTo("1800.00");
        assertThat(EtiRules.split(d("3000"), BigDecimal.ZERO)[1]).isEqualByComparingTo("3000.00");
    }
}
