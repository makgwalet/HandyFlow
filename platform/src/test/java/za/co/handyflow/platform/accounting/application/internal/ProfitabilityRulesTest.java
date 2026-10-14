package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.ProfitabilityRules.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProfitabilityRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static void eq(String exp, BigDecimal actual) { assertEquals(0, d(exp).compareTo(actual), "expected " + exp + " but was " + actual); }
    private static final UUID BRANCH = UUID.randomUUID(), SITE1 = UUID.randomUUID(), SITE2 = UUID.randomUUID();

    private static Map<UUID, UUID> tree() { Map<UUID, UUID> m = new HashMap<>(); m.put(BRANCH, null); m.put(SITE1, BRANCH); m.put(SITE2, BRANCH); return m; }

    private static List<Cell> cells() {
        return List.of(
                new Cell(SITE1, "INCOME", "REVENUE", d("1000")), new Cell(SITE1, "EXPENSE", "PAYROLL", d("600")), new Cell(SITE1, "EXPENSE", "TRAVEL", d("50")),
                new Cell(SITE2, "INCOME", "REVENUE", d("500")), new Cell(SITE2, "EXPENSE", "PAYROLL", d("550")),
                new Cell(BRANCH, "EXPENSE", "RENT", d("100")),
                new Cell(null, "EXPENSE", "COS", d("40")), new Cell(null, "EXPENSE", null, d("10")));
    }

    @Test
    void splitsIncomeAndExpensesBySubtype() {
        View v = ProfitabilityRules.build(cells(), List.of(SITE1, SITE2), tree(), false);
        Row s1 = v.rows().get(0);
        eq("1000", s1.income());
        eq("650", s1.expenseTotal());
        eq("350", s1.profit());
        eq("600", s1.expenses().get("PAYROLL"));
        eq("-50", v.rows().get(1).profit());          // a loss-maker
        eq("50", v.unallocated().expenseTotal());
        assertTrue(v.subtypes().containsAll(List.of("PAYROLL", "TRAVEL", "RENT", "COS", "OTHER")));
    }

    @Test
    void totalCountsEveryLineOnce() {
        View v = ProfitabilityRules.build(cells(), List.of(BRANCH, SITE1, SITE2), tree(), true);
        eq("1500", v.total().income());
        eq("1350", v.total().expenseTotal());   // 650 + 550 + 100 + 50
        eq("150", v.total().profit());
    }

    @Test
    void rollUpAddsSitesToTheirBranch() {
        View v = ProfitabilityRules.build(cells(), List.of(BRANCH, SITE1, SITE2), tree(), true);
        Row branch = v.rows().get(0);
        eq("1500", branch.income());
        eq("1300", branch.expenseTotal());      // sites 650 + 550 plus its own rent 100
        View own = ProfitabilityRules.build(cells(), List.of(BRANCH, SITE1, SITE2), tree(), false);
        eq("100", own.rows().get(0).expenseTotal());
        eq("0", own.rows().get(0).income());
    }

    @Test
    void directCostsAreWhateverSubtypesArePicked() {
        Row s1 = ProfitabilityRules.build(cells(), List.of(SITE1), tree(), false).rows().get(0);
        eq("600", ProfitabilityRules.direct(s1, Set.of("PAYROLL")));
        eq("650", ProfitabilityRules.direct(s1, Set.of("PAYROLL", "TRAVEL")));
        eq("0", ProfitabilityRules.direct(s1, Set.of()));
    }

    @Test
    void marginNeedsIncome() {
        eq("35.0", ProfitabilityRules.marginPercent(d("1000"), d("350")));
        eq("-10.0", ProfitabilityRules.marginPercent(d("500"), d("-50")));
        assertNull(ProfitabilityRules.marginPercent(d("0"), d("-50")));
    }
}
