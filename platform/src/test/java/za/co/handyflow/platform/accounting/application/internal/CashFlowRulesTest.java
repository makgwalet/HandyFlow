package za.co.handyflow.platform.accounting.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import za.co.handyflow.platform.accounting.application.internal.CashFlowRules.Move;

/** Finance patch F13: the cash flow statement, indirect method. */
class CashFlowRulesTest {
    static BigDecimal d(String v) { return new BigDecimal(v); }
    static Move m(String name, String type, String sub, String net) { return new Move("x", name, type, sub, d(net)); }

    // Sold goods for 1000 on credit (profit 1000, debtors +1000), then collected 600; paid 200 expenses in cash;
    // bought a vehicle for 300 in cash; took a loan of 500; owner put in 100.
    List<Move> year = List.of(
            m("Bank", "ASSET", "BANK", "600"),  // +600 +500 +100 -200 -300 = 700
            m("Debtors", "ASSET", "RECEIVABLE", "400"),
            m("Vehicles", "ASSET", "FIXED_ASSET", "300"),
            m("Loan", "LIABILITY", "LOAN", "-500"),
            m("Capital", "EQUITY", "EQUITY", "-100"),
            m("Sales", "INCOME", "SALES", "-1000"),
            m("Costs", "EXPENSE", "OTHER", "200"));

    @Test void sortsMovementsIntoSections() {
        // fix the bank figure so the journals balance: 700
        List<Move> moves = new java.util.ArrayList<>(year);
        moves.set(0, m("Bank", "ASSET", "BANK", "700"));
        CashFlowRules.Statement s = CashFlowRules.build(moves, d("1000"));
        assertEquals(d("800.00"), s.netProfit());
        assertEquals(d("400.00"), s.operatingTotal());        // 800 profit - 400 debtors
        assertEquals(d("-300.00"), s.investingTotal());
        assertEquals(d("600.00"), s.financingTotal());        // loan 500 + capital 100
        assertEquals(d("700.00"), s.netChange());
        assertEquals(d("1000.00"), s.openingCash());
        assertEquals(d("1700.00"), s.closingCash());
        assertTrue(CashFlowRules.agrees(s));
    }

    @Test void depreciationIsAddedBackAndDebtorsLabelled() {
        CashFlowRules.Statement s = CashFlowRules.build(List.of(
                m("Bank", "ASSET", "BANK", "-50"),
                m("Accumulated depreciation", "ASSET", "DEPRECIATION", "-100"),
                m("Depreciation", "EXPENSE", "OTHER", "100"),
                m("Stock", "ASSET", "INVENTORY", "50"),
                m("Sales", "INCOME", "SALES", "0")), BigDecimal.ZERO);
        assertEquals(d("-100.00"), s.netProfit());
        assertTrue(s.operating().stream().anyMatch(l -> l.label().equals("Accumulated depreciation (not paid in cash)") && l.amount().equals(d("100.00"))));
        assertTrue(s.operating().stream().anyMatch(l -> l.label().equals("Increase in Stock") && l.amount().equals(d("-50.00"))));
        assertTrue(CashFlowRules.agrees(s));
    }

    @Test void aJournalThatDoesNotBalanceShowsAsADifference() {
        CashFlowRules.Statement s = CashFlowRules.build(List.of(
                m("Bank", "ASSET", "BANK", "100"), m("Sales", "INCOME", "SALES", "-90")), BigDecimal.ZERO);
        assertFalse(CashFlowRules.agrees(s));
        assertEquals(d("-10.00"), s.difference());
    }

    @Test void zeroMovementsAreLeftOut() {
        CashFlowRules.Statement s = CashFlowRules.build(List.of(m("Debtors", "ASSET", "RECEIVABLE", "0")), d("5"));
        assertEquals(1, s.operating().size()); // only net profit
        assertEquals(d("5.00"), s.closingCash());
    }

    @Test void classification() {
        assertTrue(CashFlowRules.isCash("ASSET", "BANK"));
        assertFalse(CashFlowRules.isCash("ASSET", "RECEIVABLE"));
        assertEquals("INVESTING", CashFlowRules.section("ASSET", "FIXED_ASSET"));
        assertEquals("OPERATING", CashFlowRules.section("LIABILITY", "VAT"));
        assertEquals("FINANCING", CashFlowRules.section("LIABILITY", "LOAN"));
        assertEquals("FINANCING", CashFlowRules.section("EQUITY", null));
    }
}
