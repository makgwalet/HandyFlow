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

    // ── The information written on the certificate ─────────────────────────

    private static EtiRules.Slip slip(int y, int m, String eti, Integer etiMonth, String days, int working) {
        return new EtiRules.Slip(java.time.LocalDate.of(y, m, 1).withDayOfMonth(java.time.LocalDate.of(y, m, 1).lengthOfMonth()), new BigDecimal("4500.00"),
                new BigDecimal(eti), etiMonth, new BigDecimal(days), working);
    }

    @Test
    void nothingIsReportedWhenNoIncentiveWasClaimed() {
        assertThat(EtiRules.months(java.util.List.of(slip(2026, 3, "0", null, "22", 22)), 2027)).isEmpty();
        assertThat(EtiRules.months(java.util.List.of(), 2027)).isEmpty();
    }

    @Test
    void everyMonthWithAPayslipIsReportedOnceOneWasClaimed() {
        var months = EtiRules.months(java.util.List.of(slip(2026, 3, "1000.00", 1, "22", 22), slip(2026, 4, "0", null, "22", 22),
                slip(2026, 5, "800.00", 13, "11", 22)), 2027);
        assertThat(months.size()).isEqualTo(3);
        assertThat(months.get(0).calendarMonth()).isEqualTo(3);
        assertThat(months.get(0).cycle()).isEqualTo(1);
        assertThat(months.get(0).hours()).isEqualTo(160);
        assertThat(months.get(0).minimumWage()).isEqualTo(new BigDecimal("4836.80"));      // 160 x R30.23
        assertThat(months.get(1).cycle()).isEqualTo(0);
        assertThat(months.get(1).eti()).isEqualTo(new BigDecimal("0.00"));
        assertThat(months.get(2).cycle()).isEqualTo(2);                                      // the 13th qualifying month
        assertThat(months.get(2).hours()).isEqualTo(80);                                     // half the days paid
    }

    @Test
    void twoPayslipsInOneMonthAreOneMonthOfInformation() {
        var months = EtiRules.months(java.util.List.of(slip(2026, 3, "500.00", 1, "11", 22), slip(2026, 3, "0", null, "11", 22)), 2027);
        assertThat(months.size()).isEqualTo(1);
        assertThat(months.get(0).remuneration()).isEqualTo(new BigDecimal("9000.00"));
        assertThat(months.get(0).hours()).isEqualTo(160);
    }

    @Test
    void theHourlyMinimumWageFollowsTheTaxYear() {
        assertThat(EtiRules.nationalMinimumWage(2024)).isEqualTo(new BigDecimal("25.42"));
        assertThat(EtiRules.nationalMinimumWage(2025)).isEqualTo(new BigDecimal("27.58"));
        assertThat(EtiRules.nationalMinimumWage(2026)).isEqualTo(new BigDecimal("28.79"));
        assertThat(EtiRules.nationalMinimumWage(2027)).isEqualTo(new BigDecimal("30.23"));
        assertThat(EtiRules.nationalMinimumWage(2030)).isEqualTo(new BigDecimal("30.23"));
    }

    // ── identity papers ─────────────────────────────────────────────────────────────────────────

    private static String id(boolean valid, String type, String no, LocalDate exp, boolean other) { return EtiRules.identityProblem(valid, type, no, exp, other, END); }

    @Test
    void aValidSouthAfricanIdQualifiesWhateverElseIsOnRecord() {
        assertThat(id(true, null, null, null, false)).isNull();
        assertThat(id(true, "GENERAL_WORK", "W1", END.minusDays(5), true)).isNull();
    }

    @Test
    void anAsylumSeekerPermitOrRefugeeIdQualifiesWhileItRuns() {
        assertThat(id(false, "ASYLUM_OR_REFUGEE", "AS-1", END, false)).isNull();            // ends on the last day: still valid
        assertThat(id(false, "ASYLUM_OR_REFUGEE", "AS-1", END.plusYears(1), false)).isNull();
        assertThat(id(false, "ASYLUM_OR_REFUGEE", "AS-1", END.minusDays(1), false)).contains("ended on").contains("before the end of the month");
        assertThat(id(false, "ASYLUM_OR_REFUGEE", " ", END.plusDays(9), false)).contains("no number");
        assertThat(id(false, "ASYLUM_OR_REFUGEE", null, END.plusDays(9), false)).contains("no number");
        assertThat(id(false, "ASYLUM_OR_REFUGEE", "AS-1", null, false)).contains("no end date");
    }

    @Test
    void aPassportOrWorkVisaDoesNotQualify() {
        assertThat(id(false, "GENERAL_WORK", "W1", END.plusYears(2), false)).contains("passport or work visa does not qualify");
        assertThat(id(false, null, null, null, true)).contains("passport or work visa does not qualify");
        assertThat(id(false, "", null, null, true)).contains("passport or work visa does not qualify");
    }

    @Test
    void withNothingOnRecordTheReasonSaysSo() {
        assertThat(id(false, null, null, null, false)).contains("no valid South African ID");
        assertThat(id(false, "", null, null, false)).contains("no valid South African ID");
    }

    @Test
    void theReasonIsCarriedIntoTheResult() {
        String why = id(false, "GENERAL_WORK", "W1", END.plusYears(1), false);
        Result r = EtiRules.calculate(T, DOB_25, END, why, null, 0, d("5000"), BigDecimal.ONE);
        assertThat(r.qualifies()).isFalse();
        assertThat(r.why()).isEqualTo(why);
        assertThat(EtiRules.calculate(T, DOB_25, END, (String) null, null, 0, d("5000"), BigDecimal.ONE).amount()).isEqualByComparingTo("1500.00");
        assertThat(EtiRules.calculate(T, DOB_25, END, false, null, 0, d("5000"), BigDecimal.ONE).why()).contains("no valid South African ID");
    }

    @Test
    void anAgeOrExclusionReasonComesBeforeTheIdentityReason() {
        assertThat(EtiRules.calculate(T, LocalDate.of(1980, 1, 1), END, "x", null, 0, d("5000"), BigDecimal.ONE).why()).contains("age");
        assertThat(EtiRules.calculate(T, DOB_25, END, "x", "domestic worker", 0, d("5000"), BigDecimal.ONE).why()).contains("excluded");
    }
}
