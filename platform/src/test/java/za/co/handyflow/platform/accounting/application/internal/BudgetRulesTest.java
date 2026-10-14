package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.BudgetRules.LineIn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BudgetRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final UUID INC = UUID.randomUUID(), EXP = UUID.randomUUID(), ASSET = UUID.randomUUID();
    private static final Map<UUID, String> TYPES = Map.of(INC, "INCOME", EXP, "EXPENSE", ASSET, "ASSET");

    @Test
    void startsOnTheFirstOfAMonthWithAName() {
        assertNull(BudgetRules.createProblem("FY2027", LocalDate.of(2026, 3, 1)));
        assertNotNull(BudgetRules.createProblem("", LocalDate.of(2026, 3, 1)));
        assertNotNull(BudgetRules.createProblem("x", LocalDate.of(2026, 3, 2)));
        assertEquals(LocalDate.of(2027, 2, 1), BudgetRules.months(LocalDate.of(2026, 3, 1)).get(11));
    }

    @Test
    void linesAreIncomeOrExpenseOnlyWithoutDuplicates() {
        assertNull(BudgetRules.linesProblem(List.of(new LineIn(INC, 0, d("100")), new LineIn(EXP, 11, d("0"))), TYPES));
        assertNotNull(BudgetRules.linesProblem(List.of(new LineIn(ASSET, 0, d("1"))), TYPES));
        assertNotNull(BudgetRules.linesProblem(List.of(new LineIn(INC, 12, d("1"))), TYPES));
        assertNotNull(BudgetRules.linesProblem(List.of(new LineIn(INC, 0, d("-1"))), TYPES));
        assertNotNull(BudgetRules.linesProblem(List.of(new LineIn(INC, 0, d("1")), new LineIn(INC, 0, d("2"))), TYPES));
        assertNotNull(BudgetRules.linesProblem(List.of(new LineIn(UUID.randomUUID(), 0, d("1"))), TYPES));
        assertNotNull(BudgetRules.linesProblem(List.of(new LineIn(INC, 0, d("1.005"))), TYPES));
    }

    @Test
    void activationNeedsADraftWithLinesAndNoRival() {
        assertNull(BudgetRules.activateProblem("DRAFT", 3, false));
        assertNotNull(BudgetRules.activateProblem("ACTIVE", 3, false));
        assertNotNull(BudgetRules.activateProblem("DRAFT", 0, false));
        assertNotNull(BudgetRules.activateProblem("DRAFT", 3, true));
        assertNull(BudgetRules.editProblem("DRAFT"));
        assertNotNull(BudgetRules.editProblem("ACTIVE"));
    }

    @Test
    void varianceIsFavourableWhenIncomeBeatsAndExpenseUndershoots() {
        var inc = BudgetRules.variance("INCOME", d("1000"), d("1200"));
        assertEquals(d("200"), inc.amount());
        assertTrue(inc.favourable());
        assertEquals(d("20.0"), inc.percent());
        var exp = BudgetRules.variance("EXPENSE", d("1000"), d("1200"));
        assertEquals(d("-200"), exp.amount());
        assertFalse(exp.favourable());
        assertNull(BudgetRules.variance("EXPENSE", d("0"), d("50")).percent());
    }

    @Test
    void copiesLastYearWithUplift() {
        var r = BudgetRules.fromActuals(List.of(d("100"), d("-5"), d("200.55")), d("10"));
        assertEquals(12, r.size());
        assertEquals(d("110.00"), r.get(0));
        assertEquals(d("0.00"), r.get(1));
        assertEquals(d("220.61"), r.get(2));
        assertEquals(d("0.00"), r.get(11));
        assertNotNull(BudgetRules.upliftProblem(d("501")));
        assertNull(BudgetRules.upliftProblem(null));
    }
}
