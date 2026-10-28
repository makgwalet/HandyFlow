package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NoticePayRulesTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }
    private static final BigDecimal OWED = n("12000.00"), CAP = n("24000.00");

    @Test
    void anAmountUpToTheWholeNoticePeriodIsAccepted() {
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", n("12000.00"), OWED, CAP, false)).isNull();
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", n("24000.00"), OWED, CAP, false)).isNull();
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", n("24000.01"), OWED, CAP, false)).contains("whole notice period");
    }

    @Test
    void itNeedsEmploymentToHaveEndedWithNoticeOwed() {
        assertThat(NoticePayRules.problem("ACTIVE", "EMPLOYER_NOTICE", n("1"), OWED, CAP, false)).contains("once employment has ended");
        assertThat(NoticePayRules.problem("TERMINATED", null, n("1"), OWED, CAP, false)).contains("how employment ended");
        assertThat(NoticePayRules.problem("TERMINATED", "RESIGNATION", n("1"), OWED, CAP, false)).contains("resignation");
        assertThat(NoticePayRules.problem("TERMINATED", "SUMMARY_DISMISSAL", n("1"), OWED, CAP, false)).contains("summary dismissal");
    }

    @Test
    void itIsRefusedTwiceAndWhenNothingIsOwedOrNoAmountGiven() {
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", n("1"), OWED, CAP, true)).contains("already entered");
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", n("1"), BigDecimal.ZERO, CAP, false)).contains("whole notice period was given");
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", null, OWED, CAP, false)).contains("Enter the amount");
        assertThat(NoticePayRules.problem("TERMINATED", "EMPLOYER_NOTICE", BigDecimal.ZERO, OWED, CAP, false)).contains("Enter the amount");
    }
}
