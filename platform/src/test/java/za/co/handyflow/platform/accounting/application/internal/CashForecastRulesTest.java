package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.CashForecastRules.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CashForecastRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final Assumptions A = Assumptions.defaults();

    @Test
    void customersPayAfterTheDueDatePlusDelayOrSoonWhenOverdue() {
        assertEquals(LocalDate.of(2026, 10, 30), CashForecastRules.expectedReceipt(LocalDate.of(2026, 10, 30), TODAY, A));
        var slow = new Assumptions(10, 14, 7, 100);
        assertEquals(LocalDate.of(2026, 11, 9), CashForecastRules.expectedReceipt(LocalDate.of(2026, 10, 30), TODAY, slow));
        assertEquals(TODAY.plusDays(14), CashForecastRules.expectedReceipt(LocalDate.of(2026, 9, 1), TODAY, A));
        assertEquals(TODAY.plusDays(30), CashForecastRules.expectedReceipt(null, TODAY, A));
    }

    @Test
    void suppliersAreOwedOnTheDueDateOrSoonWhenOverdue() {
        assertEquals(LocalDate.of(2026, 10, 20), CashForecastRules.expectedPayment(LocalDate.of(2026, 10, 20), TODAY, A));
        assertEquals(TODAY.plusDays(7), CashForecastRules.expectedPayment(LocalDate.of(2026, 9, 1), TODAY, A));
    }

    @Test
    void collectionPercentScalesWhatIsExpected() {
        assertEquals(d("800.00"), CashForecastRules.collected(d("1000"), new Assumptions(0, 14, 7, 80)));
    }

    @Test
    void weeksRunFromTodayAndCarryTheBalance() {
        var items = List.of(
                new Item(TODAY.plusDays(2), d("500"), "IN", "Invoice 1", "INVOICE"),
                new Item(TODAY.plusDays(8), d("1500"), "OUT", "Payroll", "MANUAL"),
                new Item(TODAY.plusDays(7 * 13), d("100"), "OUT", "later", "MANUAL"),
                new Item(TODAY.minusDays(3), d("50"), "IN", "past dated", "MANUAL"));
        var f = CashForecastRules.build(d("1000"), TODAY, items, null);
        assertEquals(13, f.weeks().size());
        assertEquals(d("1550"), f.weeks().get(0).closing());
        assertEquals(d("50"), f.weeks().get(1).closing());
        assertEquals(d("50"), f.weeks().get(12).closing());
        assertEquals(d("100"), f.laterOut());
        assertNull(f.firstNegativeWeek());
        assertEquals(d("50"), f.lowest());
    }

    @Test
    void warnsWhenCashRunsOutOrFallsBelowTheMinimum() {
        var out = List.of(new Item(TODAY.plusDays(15), d("2000"), "OUT", "Rent", "MANUAL"));
        var f = CashForecastRules.build(d("1000"), TODAY, out, null);
        assertEquals(Integer.valueOf(2), f.firstNegativeWeek());
        assertTrue(f.warnings().get(0).contains("week 3"));
        var g = CashForecastRules.build(d("1000"), TODAY, List.of(new Item(TODAY.plusDays(1), d("600"), "OUT", "x", "MANUAL")), d("500"));
        assertNull(g.firstNegativeWeek());
        assertTrue(g.warnings().get(0).contains("below"));
        assertTrue(CashForecastRules.build(d("1000"), TODAY, List.of(), d("500")).warnings().isEmpty());
    }

    @Test
    void validatesInputs() {
        assertNull(CashForecastRules.itemProblem(TODAY, d("1"), "IN", "x"));
        assertNotNull(CashForecastRules.itemProblem(TODAY, d("0"), "IN", "x"));
        assertNotNull(CashForecastRules.itemProblem(TODAY, d("1"), "SIDEWAYS", "x"));
        assertNotNull(CashForecastRules.assumptionsProblem(new Assumptions(0, 14, 7, 101)));
        assertNull(CashForecastRules.assumptionsProblem(A));
    }
}
