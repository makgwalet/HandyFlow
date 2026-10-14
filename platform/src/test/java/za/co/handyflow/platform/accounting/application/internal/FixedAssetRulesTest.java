package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.FixedAssetRules.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FixedAssetRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static void eq(String exp, BigDecimal actual) { assertEquals(0, d(exp).compareTo(actual), "expected " + exp + " but was " + actual); }
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final String SL = FixedAssetRules.STRAIGHT_LINE, RB = FixedAssetRules.REDUCING_BALANCE;

    @Test
    void validatesNewAssets() {
        assertNull(FixedAssetRules.saveProblem("Bakkie", LocalDate.of(2026, 1, 5), d("300000"), d("60000"), SL, 60, null, TODAY));
        assertNotNull(FixedAssetRules.saveProblem("", LocalDate.of(2026, 1, 5), d("1"), d("0"), SL, 12, null, TODAY));
        assertNotNull(FixedAssetRules.saveProblem("x", LocalDate.of(2027, 1, 5), d("1"), d("0"), SL, 12, null, TODAY), "future");
        assertNotNull(FixedAssetRules.saveProblem("x", LocalDate.of(2026, 1, 5), d("0"), d("0"), SL, 12, null, TODAY));
        assertNotNull(FixedAssetRules.saveProblem("x", LocalDate.of(2026, 1, 5), d("100"), d("100"), SL, 12, null, TODAY), "residual = cost");
        assertNotNull(FixedAssetRules.saveProblem("x", LocalDate.of(2026, 1, 5), d("100"), d("0"), SL, null, null, TODAY));
        assertNotNull(FixedAssetRules.saveProblem("x", LocalDate.of(2026, 1, 5), d("100"), d("0"), RB, null, null, TODAY));
        assertNull(FixedAssetRules.saveProblem("x", LocalDate.of(2026, 1, 5), d("100"), d("0"), RB, null, d("25"), TODAY));
        assertNotNull(FixedAssetRules.saveProblem("x", LocalDate.of(2026, 1, 5), d("100"), d("0"), "MAGIC", 12, null, TODAY));
    }

    @Test
    void startsInTheMonthOfAcquisition() {
        LocalDate acq = LocalDate.of(2026, 3, 20);
        assertEquals(0, FixedAssetRules.monthsThrough(acq, LocalDate.of(2026, 2, 1)));
        assertEquals(1, FixedAssetRules.monthsThrough(acq, LocalDate.of(2026, 3, 1)));
        assertEquals(8, FixedAssetRules.monthsThrough(acq, LocalDate.of(2026, 10, 1)));
    }

    @Test
    void straightLineIsEvenAndStopsAtResidual() {
        eq("4000.00", FixedAssetRules.cumulative(SL, d("300000"), d("60000"), 60, null, 1));
        eq("48000.00", FixedAssetRules.cumulative(SL, d("300000"), d("60000"), 60, null, 12));
        eq("240000.00", FixedAssetRules.cumulative(SL, d("300000"), d("60000"), 60, null, 60));
        eq("240000.00", FixedAssetRules.cumulative(SL, d("300000"), d("60000"), 60, null, 90));
    }

    @Test
    void straightLineRoundingNeverLosesACent() {
        // 1000 over 3 months: 333.33, 666.67, 1000.00
        BigDecimal acc = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (int m = 1; m <= 3; m++) {
            BigDecimal c = FixedAssetRules.chargeFor(SL, d("1000"), d("0"), 3, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, m, 1), acc);
            sum = sum.add(c);
            acc = acc.add(c);
        }
        eq("1000.00", sum);
    }

    @Test
    void reducingBalanceTakesAShareOfWhatIsLeftAndKeepsResidual() {
        BigDecimal one = FixedAssetRules.cumulative(RB, d("12000"), d("0"), null, d("25"), 1);
        eq("250.00", one);                       // 12000 x 25% / 12
        BigDecimal two = FixedAssetRules.cumulative(RB, d("12000"), d("0"), null, d("25"), 2);
        eq("494.79", two);                       // 12000 x (1 - (1 - 0.25/12)^2)
        eq("2000.00", FixedAssetRules.cumulative(RB, d("12000"), d("10000"), null, d("50"), 400).min(d("2000")));
        assertTrue(FixedAssetRules.cumulative(RB, d("12000"), d("10000"), null, d("50"), 400).compareTo(d("2000")) <= 0);
    }

    @Test
    void aSkippedMonthIsCaughtUp() {
        LocalDate acq = LocalDate.of(2026, 1, 10);
        // No run for Jan or Feb; the March run takes three months at once.
        eq("3000.00", FixedAssetRules.chargeFor(SL, d("12000"), d("0"), 12, null, acq, LocalDate.of(2026, 3, 1), BigDecimal.ZERO));
        // Nothing more is charged twice.
        eq("0.00", FixedAssetRules.chargeFor(SL, d("12000"), d("0"), 12, null, acq, LocalDate.of(2026, 3, 1), d("3000")));
    }

    @Test
    void anAssetAcquiredLaterGetsNothingForEarlierMonths() {
        eq("0.00", FixedAssetRules.chargeFor(SL, d("12000"), d("0"), 12, null, LocalDate.of(2026, 5, 3), LocalDate.of(2026, 4, 1), BigDecimal.ZERO));
    }

    @Test
    void runsAreWholeMonthsInOrderAndNotInTheFuture() {
        assertNull(FixedAssetRules.runProblem(LocalDate.of(2026, 9, 1), TODAY, LocalDate.of(2026, 8, 1)));
        assertNotNull(FixedAssetRules.runProblem(LocalDate.of(2026, 9, 15), TODAY, null));
        assertNotNull(FixedAssetRules.runProblem(LocalDate.of(2026, 11, 1), TODAY, null));
        assertNotNull(FixedAssetRules.runProblem(LocalDate.of(2026, 8, 1), TODAY, LocalDate.of(2026, 8, 1)));
        assertNotNull(FixedAssetRules.runProblem(LocalDate.of(2026, 7, 1), TODAY, LocalDate.of(2026, 8, 1)));
        assertEquals(LocalDate.of(2026, 9, 1), FixedAssetRules.nextPeriod(LocalDate.of(2026, 8, 1), null, TODAY));
        assertEquals(LocalDate.of(2026, 3, 1), FixedAssetRules.nextPeriod(null, LocalDate.of(2026, 3, 20), TODAY));
        assertNull(FixedAssetRules.nextPeriod(LocalDate.of(2026, 10, 1), null, TODAY));
    }

    @Test
    void runJournalBalancesAndKeepsCostCentres() {
        UUID exp = UUID.randomUUID(), acc = UUID.randomUUID(), cc = UUID.randomUUID();
        var lines = FixedAssetRules.runLines(List.of(
                new Charge(UUID.randomUUID(), "FA-1", exp, acc, cc, d("100.00")),
                new Charge(UUID.randomUUID(), "FA-2", exp, acc, null, d("50.50")),
                new Charge(UUID.randomUUID(), "FA-3", exp, acc, null, d("0.00"))), "Oct 2026");
        assertEquals(3, lines.size(), "two debits and one combined credit");
        assertEquals(cc, lines.get(0).costCentreId());
        eq("150.50", lines.stream().map(l -> l.debit() == null ? BigDecimal.ZERO : l.debit()).reduce(BigDecimal.ZERO, BigDecimal::add));
        eq("150.50", lines.stream().map(l -> l.credit() == null ? BigDecimal.ZERO : l.credit()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void disposalGainAndLossBalance() {
        UUID cost = UUID.randomUUID(), acc = UUID.randomUUID(), bank = UUID.randomUUID(), gl = UUID.randomUUID();
        for (String proceeds : new String[]{"0", "50000", "100000", "130000"}) {
            var lines = FixedAssetRules.disposalLines(cost, acc, bank, gl, d("200000"), d("90000"), d(proceeds), null, "FA-1");
            BigDecimal dr = lines.stream().map(l -> l.debit() == null ? BigDecimal.ZERO : l.debit()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cr = lines.stream().map(l -> l.credit() == null ? BigDecimal.ZERO : l.credit()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, dr.compareTo(cr), "balanced for proceeds " + proceeds);
        }
        eq("-20000.00", FixedAssetRules.gainOrLoss(d("90000"), d("200000"), d("90000")));  // book value 110000
        eq("20000.00", FixedAssetRules.gainOrLoss(d("130000"), d("200000"), d("90000")));
    }

    @Test
    void disposalNeedsDepreciationUpToDate() {
        UUID acct = UUID.randomUUID();
        LocalDate acq = LocalDate.of(2025, 1, 1);
        assertNull(FixedAssetRules.disposalProblem("ACTIVE", acq, LocalDate.of(2026, 10, 5), d("10"), acct, acct, LocalDate.of(2026, 9, 1), TODAY));
        assertNotNull(FixedAssetRules.disposalProblem("ACTIVE", acq, LocalDate.of(2026, 10, 5), d("10"), acct, acct, LocalDate.of(2026, 7, 1), TODAY));
        assertNotNull(FixedAssetRules.disposalProblem("DISPOSED", acq, LocalDate.of(2026, 10, 5), d("10"), acct, acct, null, TODAY));
        assertNotNull(FixedAssetRules.disposalProblem("ACTIVE", acq, LocalDate.of(2026, 10, 5), d("10"), null, acct, LocalDate.of(2026, 9, 1), TODAY), "proceeds need an account");
        assertNull(FixedAssetRules.disposalProblem("ACTIVE", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), d("0"), null, acct, null, TODAY), "bought and sold in the same month");
        assertNotNull(FixedAssetRules.disposalProblem("ACTIVE", acq, LocalDate.of(2024, 12, 31), d("0"), null, acct, null, TODAY), "before acquired");
    }

    @Test
    void registerVersusLedger() {
        eq("500.00", FixedAssetRules.difference(d("10500"), d("10000")));
        eq("-10000.00", FixedAssetRules.difference(null, d("10000")));
    }
}
