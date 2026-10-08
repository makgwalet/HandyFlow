package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Totals;
import za.co.handyflow.platform.clinic.application.internal.ClaimSummaryRules.Fact;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimSummaryRulesTest {

    private static BigDecimal r(String v) { return new BigDecimal(v); }
    private static Totals t(String paid, String off, String credit) { return new Totals(r(paid), r(off), r(credit)); }

    @Test
    void noClaimsIsAllZero() {
        var s = ClaimSummaryRules.summarise(List.of());
        assertEquals(0L, s.total()); assertEquals(0, s.outstanding().signum()); assertEquals(0, s.paid().signum()); assertEquals(0L, s.rejected());
    }

    @Test
    void anUnansweredClaimCountsItsWholeSchemePortionAsOutstanding() {
        var s = ClaimSummaryRules.summarise(List.of(new Fact("DRAFT", r("500.00"), null), new Fact("SUBMITTED", r("250.00"), Totals.NONE)));
        assertEquals(0, r("750.00").compareTo(s.outstanding()));
    }

    @Test
    void anAcceptedOrPartlyPaidClaimCountsWhatTheSchemeStillOwes() {
        var s = ClaimSummaryRules.summarise(List.of(
                new Fact("ACCEPTED", r("1000.00"), Totals.NONE),                      // owes 1000
                new Fact("PARTIAL", r("1000.00"), t("400.00", "100.00", "0.00"))));   // owes 500
        assertEquals(0, r("1500.00").compareTo(s.outstanding()));
        assertEquals(0, r("400.00").compareTo(s.paid()));
    }

    @Test
    void paidClosedAndRejectedClaimsOweNothingButPaidMoneyStillCounts() {
        var s = ClaimSummaryRules.summarise(List.of(
                new Fact("PAID", r("800.00"), t("800.00", "0.00", "0.00")),
                new Fact("CLOSED", r("600.00"), t("300.00", "300.00", "0.00")),
                new Fact("REJECTED", r("200.00"), null),
                new Fact("VOIDED", r("100.00"), null)));
        assertEquals(0, s.outstanding().signum());
        assertEquals(0, r("1100.00").compareTo(s.paid()));
        assertEquals(1L, s.rejected());
        assertEquals(4L, s.total());
    }

    @Test
    void moreWrittenOffThanOwedNeverGoesNegative() {
        var s = ClaimSummaryRules.summarise(List.of(new Fact("PARTIAL", r("100.00"), t("0.00", "150.00", "0.00"))));
        assertEquals(0, s.outstanding().signum());
    }
}
