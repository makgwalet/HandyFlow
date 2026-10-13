package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.LedgerMath.Line;
import za.co.handyflow.platform.debtcollection.application.internal.LedgerMath.Totals;
import za.co.handyflow.platform.debtcollection.domain.model.LedgerEntryType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerMathTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static Line pay(String principal, String fees) {
        return new Line(LedgerEntryType.PAYMENT, d(principal).add(d(fees)), d(principal), d(fees));
    }

    @Test
    void emptyLedgerIsAllZero() {
        Totals t = LedgerMath.summarise(List.of());
        assertEquals(0, t.netRecovered().signum());
        assertEquals(0, t.feesOutstanding().signum());
    }

    @Test
    void feesOutstandingIsChargedMinusWaivedMinusRecovered() {
        Totals t = LedgerMath.summarise(List.of(
                new Line(LedgerEntryType.FEE_CHARGE, d("100.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                new Line(LedgerEntryType.FEE_WAIVER, d("20.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                pay("500.00", "30.00")));
        assertEquals(d("530.00"), t.netRecovered());
        assertEquals(d("500.00"), t.principalRecoveredNet());
        assertEquals(d("30.00"), t.feesRecoveredNet());
        assertEquals(d("50.00"), t.feesOutstanding()); // 100 - 20 - 30
    }

    @Test
    void reversalUndoesExactlyTheOriginalSplit() {
        Totals t = LedgerMath.summarise(List.of(
                new Line(LedgerEntryType.FEE_CHARGE, d("100.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                pay("500.00", "100.00"),
                new Line(LedgerEntryType.REVERSAL, d("600.00"), d("500.00"), d("100.00"))));
        assertEquals(0, t.netRecovered().signum());
        assertEquals(0, t.principalRecoveredNet().signum());
        assertEquals(d("100.00"), t.feesOutstanding()); // the fee is owed again
        assertEquals(d("600.00"), t.reversals());
    }

    @Test
    void feesOutstandingNeverGoesNegative() {
        Totals t = LedgerMath.summarise(List.of(
                new Line(LedgerEntryType.FEE_CHARGE, d("10.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                new Line(LedgerEntryType.FEE_WAIVER, d("10.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                pay("0.00", "5.00")));
        assertEquals(0, t.feesOutstanding().signum());
    }
}
