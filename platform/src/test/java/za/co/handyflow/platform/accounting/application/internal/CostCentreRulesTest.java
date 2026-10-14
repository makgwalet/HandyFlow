package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.CostCentreRules.Cell;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CostCentreRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final UUID HQ = UUID.randomUUID(), DBN = UUID.randomUUID(), SALES = UUID.randomUUID(), REV = UUID.randomUUID(), FUEL = UUID.randomUUID();

    private static Map<UUID, UUID> tree() {
        Map<UUID, UUID> m = new HashMap<>();
        m.put(HQ, null);          // branch
        m.put(DBN, null);         // branch
        m.put(SALES, DBN);        // department inside Durban
        return m;
    }

    @Test
    void validatesCodeNameAndKind() {
        assertNull(CostCentreRules.saveProblem("DBN-01", "Durban", "BRANCH"));
        assertNotNull(CostCentreRules.saveProblem("", "x", "BRANCH"));
        assertNotNull(CostCentreRules.saveProblem("a b", "x", "BRANCH"));
        assertNotNull(CostCentreRules.saveProblem("A", " ", "BRANCH"));
        assertNotNull(CostCentreRules.saveProblem("A", "x", "TEAM"));
        assertNotNull(CostCentreRules.saveProblem("A".repeat(21), "x", "BRANCH"));
    }

    @Test
    void refusesLoopsAndUnknownParents() {
        Map<UUID, UUID> t = tree();
        assertNull(CostCentreRules.parentProblem(SALES, HQ, t));
        assertNotNull(CostCentreRules.parentProblem(DBN, SALES, t), "Durban inside its own department");
        assertNotNull(CostCentreRules.parentProblem(HQ, HQ, t));
        assertNotNull(CostCentreRules.parentProblem(HQ, UUID.randomUUID(), t));
        assertNull(CostCentreRules.parentProblem(null, DBN, t), "a new centre has no id yet");
    }

    @Test
    void limitsNesting() {
        Map<UUID, UUID> t = new HashMap<>();
        UUID prev = null;
        List<UUID> chain = new ArrayList<>();
        for (int i = 0; i < CostCentreRules.MAX_DEPTH; i++) { UUID id = UUID.randomUUID(); t.put(id, prev); chain.add(id); prev = id; }
        assertNotNull(CostCentreRules.parentProblem(null, prev, t), "one level too deep");
        assertNull(CostCentreRules.parentProblem(null, chain.get(CostCentreRules.MAX_DEPTH - 2), t));
    }

    @Test
    void descendantsIncludeTheCentreItself() {
        assertEquals(Set.of(DBN, SALES), CostCentreRules.withDescendants(DBN, tree()));
        assertEquals(Set.of(HQ), CostCentreRules.withDescendants(HQ, tree()));
    }

    private static List<Cell> sample() {
        return List.of(
                new Cell(REV, "4000", "Sales", "INCOME", HQ, d("1000.00")),
                new Cell(REV, "4000", "Sales", "INCOME", DBN, d("500.00")),
                new Cell(REV, "4000", "Sales", "INCOME", null, d("50.00")),
                new Cell(FUEL, "5100", "Fuel", "EXPENSE", DBN, d("200.00")),
                new Cell(FUEL, "5100", "Fuel", "EXPENSE", SALES, d("100.00")));
    }

    @Test
    void pivotSplitsByCentreAndKeepsUnallocated() {
        var p = CostCentreRules.pivot(sample(), List.of(HQ, DBN, SALES), tree(), false);
        assertEquals(0, d("1550.00").compareTo(p.incomeTotal()));
        assertEquals(0, d("300.00").compareTo(p.expenseTotal()));
        assertEquals(0, d("1250.00").compareTo(p.resultTotal()));
        // columns: HQ, Durban, Sales, Unallocated
        var sales = p.income().get(0).amounts();
        assertEquals(4, sales.size());
        assertEquals(0, d("1000").compareTo(sales.get(0)));
        assertEquals(0, d("500").compareTo(sales.get(1)));
        assertEquals(0, d("0").compareTo(sales.get(2)));
        assertEquals(0, d("50").compareTo(sales.get(3)));
        // Durban result = 500 - 200
        assertEquals(0, d("300").compareTo(p.resultTotals().get(1)));
    }

    @Test
    void rollUpAddsChildrenToTheParentButNotToTheTotal() {
        var p = CostCentreRules.pivot(sample(), List.of(HQ, DBN, SALES), tree(), true);
        var fuel = p.expenses().get(0);
        assertEquals(0, d("300").compareTo(fuel.amounts().get(1)), "Durban includes its Sales department");
        assertEquals(0, d("100").compareTo(fuel.amounts().get(2)));
        assertEquals(0, d("300").compareTo(fuel.total()), "total counts each line once");
        assertEquals(0, d("1250.00").compareTo(p.resultTotal()));
    }

    @Test
    void zeroCellsAreIgnored() {
        var p = CostCentreRules.pivot(List.of(new Cell(REV, "4000", "Sales", "INCOME", HQ, d("0.00"))), List.of(HQ), tree(), false);
        assertTrue(p.income().isEmpty());
    }
}
