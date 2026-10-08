package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Open;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Share;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Totals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimLedgerRulesTest {

    static BigDecimal d(String s) { return new BigDecimal(s); }
    static Totals t(String paid, String wo, String cr) { return new Totals(d(paid), d(wo), d(cr)); }
    static Open open(UUID id, long day, String owed) { return new Open(id, Instant.ofEpochSecond(day * 86400), d(owed)); }

    @Test void outstandingIsWhatTheSchemeStillOwes() {
        assertEquals(d("600.00"), ClaimLedgerRules.outstanding(d("1000"), t("300", "50", "50")));
        assertEquals(d("0.00"), ClaimLedgerRules.outstanding(d("1000"), t("1000", "0", "0")));
        assertEquals(d("0.00"), ClaimLedgerRules.outstanding(d("100"), t("200", "0", "0")), "never negative");
    }

    @Test void markPaidReceivesTheRemainingBalance() {
        assertEquals(d("700.00"), ClaimLedgerRules.markPaid("PARTIAL", d("700.00")));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.markPaid("DRAFT", d("700")));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.markPaid("ACCEPTED", d("0")));
    }

    @Test void partialMustBeBelowTheBalance() {
        assertEquals(d("400.00"), ClaimLedgerRules.partial("ACCEPTED", d("1000"), d("400")));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.partial("ACCEPTED", d("1000"), d("1000")));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.partial("ACCEPTED", d("1000"), null));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.partial("ACCEPTED", d("1000"), d("0")));
    }

    @Test void writeOffAndCreditNeverExceedTheBalance() {
        assertEquals(d("250.00"), ClaimLedgerRules.writeOff("PARTIAL", d("250"), d("250")));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.writeOff("PARTIAL", d("250"), d("250.01")));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.writeOff("REJECTED", d("250"), d("10")));
        assertEquals(d("250.00"), ClaimLedgerRules.creditNote("REJECTED", d("250"), d("250")));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.creditNote("PAID", d("0"), d("10")));
    }

    @Test void voidOnlyBeforeMoneyMoves() {
        assertDoesNotThrow(() -> ClaimLedgerRules.requireVoidable("SUBMITTED", Totals.NONE));
        assertDoesNotThrow(() -> ClaimLedgerRules.requireVoidable("REJECTED", Totals.NONE));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.requireVoidable("PARTIAL", t("10", "0", "0")));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.requireVoidable("ACCEPTED", t("0", "0", "5")));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.requireVoidable("PAID", Totals.NONE));
        assertThrows(IllegalStateException.class, () -> ClaimLedgerRules.requireVoidable("VOIDED", Totals.NONE));
    }

    @Test void statusFollowsTheLedger() {
        assertEquals("PARTIAL", ClaimLedgerRules.statusAfter("ACCEPTED", d("1000"), t("400", "0", "0")));
        assertEquals("PAID", ClaimLedgerRules.statusAfter("PARTIAL", d("1000"), t("1000", "0", "0")));
        assertEquals("CLOSED", ClaimLedgerRules.statusAfter("PARTIAL", d("1000"), t("700", "300", "0")));
        assertEquals("CLOSED", ClaimLedgerRules.statusAfter("REJECTED", d("1000"), t("0", "0", "1000")));
        assertEquals("REJECTED", ClaimLedgerRules.statusAfter("REJECTED", d("1000"), t("0", "0", "400")));
        assertEquals("ACCEPTED", ClaimLedgerRules.statusAfter("ACCEPTED", d("1000"), Totals.NONE));
        assertEquals("ACCEPTED", ClaimLedgerRules.statusAfter("ACCEPTED", d("1000"), t("0", "200", "0")));
        assertEquals("VOIDED", ClaimLedgerRules.statusAfter("VOIDED", d("1000"), Totals.NONE));
    }

    @Test void reasonNeedsTenToFiveHundredCharacters() {
        assertEquals("Scheme short-paid the tariff", ClaimLedgerRules.reason("  Scheme short-paid the tariff "));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.reason("short"));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.reason(null));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.reason("x".repeat(501)));
    }

    @Test void oldestFirstPaysEachClaimInFullBeforeTheNext() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<Share> plan = ClaimLedgerRules.oldestFirst(d("1200"), List.of(open(c, 30, "500"), open(a, 10, "800"), open(b, 20, "400")));
        assertEquals(2, plan.size());
        assertEquals(a, plan.get(0).claimId());
        assertEquals(d("800.00"), plan.get(0).amount());
        assertEquals(b, plan.get(1).claimId());
        assertEquals(d("400.00"), plan.get(1).amount());
    }

    @Test void aShortPaymentPartPaysTheLastClaimNeverSpreads() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<Share> plan = ClaimLedgerRules.oldestFirst(d("1000"), List.of(open(a, 1, "800"), open(b, 2, "800")));
        assertEquals(d("800.00"), plan.get(0).amount());
        assertEquals(d("200.00"), plan.get(1).amount());
    }

    @Test void moneyBeyondEverythingOwedIsRefused() {
        UUID a = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.oldestFirst(d("900"), List.of(open(a, 1, "800"))));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.oldestFirst(d("0"), List.of(open(a, 1, "800"))));
    }

    @Test void claimsWithNothingOwedAreSkipped() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<Share> plan = ClaimLedgerRules.oldestFirst(d("100"), List.of(open(a, 1, "0"), open(b, 2, "300")));
        assertEquals(1, plan.size());
        assertEquals(b, plan.get(0).claimId());
    }

    @Test void aManualSplitNeedsAReasonAndMustAddUp() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<Open> o = List.of(open(a, 1, "800"), open(b, 2, "400"));
        List<Share> ok = ClaimLedgerRules.manual(d("500"), o, List.of(new Share(b, d("400")), new Share(a, d("100"))), "Scheme remittance names these two");
        assertEquals(2, ok.size());
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.manual(d("500"), o, List.of(new Share(b, d("400")), new Share(a, d("100"))), "no"));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.manual(d("500"), o, List.of(new Share(b, d("400"))), "Scheme remittance names one"));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.manual(d("500"), o, List.of(new Share(b, d("500"))), "Scheme remittance names one"));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.manual(d("100"), o, List.of(new Share(UUID.randomUUID(), d("100"))), "Scheme remittance names one"));
        assertThrows(IllegalArgumentException.class, () -> ClaimLedgerRules.manual(d("200"), o, List.of(new Share(a, d("100")), new Share(a, d("100"))), "Scheme remittance names one"));
    }
}
