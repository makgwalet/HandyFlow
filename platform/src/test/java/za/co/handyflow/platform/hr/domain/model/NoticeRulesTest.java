package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeRulesTest {

    private static final LocalDate START = LocalDate.of(2024, 3, 1);

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }
    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @Test
    void theNoticeRequiredGrowsWithService() {
        assertThat(NoticeRules.requiredDays(START, null, d(2024, 8, 31))).isEqualTo(7);
        assertThat(NoticeRules.requiredDays(START, null, d(2024, 9, 1))).isEqualTo(7);
        assertThat(NoticeRules.requiredDays(START, null, d(2024, 9, 2))).isEqualTo(14);
        assertThat(NoticeRules.requiredDays(START, null, d(2025, 2, 28))).isEqualTo(14);
        assertThat(NoticeRules.requiredDays(START, null, d(2025, 3, 1))).isEqualTo(28);
        assertThat(NoticeRules.requiredDays(START, null, d(2026, 10, 1))).isEqualTo(28);
    }

    @Test
    void serviceIsMeasuredOnTheDayNoticeWasGiven() {
        assertThat(NoticeRules.requiredDays(START, d(2025, 2, 20), d(2025, 3, 20))).isEqualTo(14);
        assertThat(NoticeRules.requiredDays(START, d(2025, 3, 20), d(2025, 3, 25))).isEqualTo(28);
    }

    @Test
    void noticeGivenIsTheDaysBetweenTheDateAndTheLastDay() {
        assertThat(NoticeRules.givenDays(d(2026, 9, 1), d(2026, 9, 15))).isEqualTo(14);
        assertThat(NoticeRules.givenDays(null, d(2026, 9, 15))).isEqualTo(0);
        assertThat(NoticeRules.givenDays(d(2026, 9, 15), d(2026, 9, 15))).isEqualTo(0);
    }

    @Test
    void onlyAnEmployerEndingEmploymentOwesPayInPlaceOfNotice() {
        LocalDate last = d(2026, 9, 15);
        assertThat(NoticeRules.shortfallDays("EMPLOYER_NOTICE", START, d(2026, 9, 1), last)).isEqualTo(14);
        assertThat(NoticeRules.shortfallDays("EMPLOYER_NOTICE", START, null, last)).isEqualTo(28);
        assertThat(NoticeRules.shortfallDays("EMPLOYER_NOTICE", START, d(2026, 8, 1), last)).isEqualTo(0);
        assertThat(NoticeRules.shortfallDays("RESIGNATION", START, null, last)).isEqualTo(0);
        assertThat(NoticeRules.shortfallDays("SUMMARY_DISMISSAL", START, null, last)).isEqualTo(0);
        assertThat(NoticeRules.shortfallDays(null, START, null, last)).isEqualTo(0);
    }

    @Test
    void payIsAWeeksPayForEverySevenDays() {
        assertThat(NoticeRules.pay(n("26000"), 7).compareTo(n("6000.00"))).isEqualTo(0);
        assertThat(NoticeRules.pay(n("26000"), 28).compareTo(n("24000.00"))).isEqualTo(0);
        assertThat(NoticeRules.pay(n("26000"), 0).signum()).isEqualTo(0);
        assertThat(NoticeRules.pay(null, 7).signum()).isEqualTo(0);
    }

    @Test
    void theAnswersAreChecked() {
        LocalDate last = d(2026, 9, 15);
        assertThat(NoticeRules.problem(null, null, last, START)).isNull();
        assertThat(NoticeRules.problem("RESIGNATION", d(2026, 9, 1), last, START)).isNull();
        assertThat(NoticeRules.problem("BOGUS", null, last, START)).contains("how employment ended");
        assertThat(NoticeRules.problem(null, d(2026, 9, 1), last, START)).contains("how employment ended");
        assertThat(NoticeRules.problem("RESIGNATION", d(2026, 9, 16), last, START)).contains("after the last day");
        assertThat(NoticeRules.problem("RESIGNATION", d(2024, 2, 1), last, START)).contains("before the start");
    }

    @Test
    void theSummaryOnlyExistsWhenTheEmployerEndedIt() {
        LocalDate last = d(2026, 9, 15);
        assertThat(NoticeRules.summary("RESIGNATION", n("26000"), START, null, last)).isNull();
        assertThat(NoticeRules.summary(null, n("26000"), START, null, last)).isNull();
        String s = NoticeRules.summary("EMPLOYER_NOTICE", n("26000"), START, d(2026, 9, 1), last);
        assertThat(s).contains("28 days").contains("14 days").contains("12000.00");
        assertThat(NoticeRules.summary("EMPLOYER_NOTICE", n("26000"), START, d(2026, 8, 1), last)).contains("No pay in place of notice is owed");
    }
}
