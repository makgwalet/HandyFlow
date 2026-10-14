package za.co.handyflow.platform.expenses.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.expenses.application.internal.ExpensePolicyRules.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpensePolicyRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final List<String> CATS = List.of("TRAVEL", "MEALS", "FUEL");

    @Test
    void aCategoryPolicyBeatsTheDefault() {
        Policy any = new Policy("*", d("1000"), "WARN", null, null);
        Policy meals = new Policy("MEALS", d("300"), "BLOCK", null, null);
        assertSame(meals, ExpensePolicyRules.pick(List.of(any, meals), "MEALS"));
        assertSame(any, ExpensePolicyRules.pick(List.of(any, meals), "FUEL"));
        assertNull(ExpensePolicyRules.pick(List.of(meals), "FUEL"));
    }

    @Test
    void aLimitBlocksOrWarnsAsConfigured() {
        Policy block = new Policy("MEALS", d("300"), "BLOCK", null, null);
        Policy warn = new Policy("MEALS", d("300"), "WARN", null, null);
        assertTrue(ExpensePolicyRules.blocked(ExpensePolicyRules.check(block, d("350"), TODAY, TODAY, true)));
        List<Finding> w = ExpensePolicyRules.check(warn, d("350"), TODAY, TODAY, true);
        assertEquals(1, w.size());
        assertFalse(ExpensePolicyRules.blocked(w));
        assertTrue(ExpensePolicyRules.check(block, d("300"), TODAY, TODAY, true).isEmpty());
    }

    @Test
    void aReceiptIsRequiredAboveTheThreshold() {
        Policy p = new Policy("FUEL", null, null, d("50"), null);
        assertTrue(ExpensePolicyRules.blocked(ExpensePolicyRules.check(p, d("80"), TODAY, TODAY, false)));
        assertTrue(ExpensePolicyRules.check(p, d("80"), TODAY, TODAY, true).isEmpty());
        assertTrue(ExpensePolicyRules.check(p, d("50"), TODAY, TODAY, false).isEmpty());
        Policy always = new Policy("FUEL", null, null, d("0"), null);
        assertTrue(ExpensePolicyRules.blocked(ExpensePolicyRules.check(always, d("1"), TODAY, TODAY, false)));
        assertTrue(ExpensePolicyRules.blockText(ExpensePolicyRules.check(always, d("1"), TODAY, TODAY, false)).contains("every claim"));
    }

    @Test
    void oldExpensesAreFlagged() {
        Policy p = new Policy("*", null, null, null, 60);
        assertEquals("OLD", ExpensePolicyRules.check(p, d("10"), TODAY.minusDays(61), TODAY, true).get(0).code());
        assertTrue(ExpensePolicyRules.check(p, d("10"), TODAY.minusDays(60), TODAY, true).isEmpty());
    }

    @Test
    void noPolicyMeansNoFindings() {
        assertTrue(ExpensePolicyRules.check(null, d("99999"), TODAY, TODAY, false).isEmpty());
    }

    @Test
    void checksAPolicyBeforeSaving() {
        assertNull(ExpensePolicyRules.policyProblem("MEALS", d("300"), "BLOCK", null, null, CATS));
        assertNull(ExpensePolicyRules.policyProblem("*", null, null, d("0"), null, CATS));
        assertNotNull(ExpensePolicyRules.policyProblem("BOGUS", d("300"), "BLOCK", null, null, CATS));
        assertNotNull(ExpensePolicyRules.policyProblem("MEALS", d("0"), "BLOCK", null, null, CATS));
        assertNotNull(ExpensePolicyRules.policyProblem("MEALS", d("300"), null, null, null, CATS));
        assertNotNull(ExpensePolicyRules.policyProblem("MEALS", null, null, d("-1"), null, CATS));
        assertNotNull(ExpensePolicyRules.policyProblem("MEALS", null, null, null, 0, CATS));
        assertNotNull(ExpensePolicyRules.policyProblem("MEALS", null, null, null, null, CATS));
    }

    @Test
    void recognisesDuplicates() {
        LocalDate d0 = LocalDate.of(2026, 10, 10);
        assertEquals("SAME", ExpensePolicyRules.duplicateKind(d("120"), d0, "MEALS", d("120.00"), d0, "MEALS"));
        assertEquals("NEAR", ExpensePolicyRules.duplicateKind(d("120"), d0, "MEALS", d("120"), d0.plusDays(3), "MEALS"));
        assertEquals("NEAR", ExpensePolicyRules.duplicateKind(d("120"), d0, "MEALS", d("120"), d0, "FUEL"));
        assertNull(ExpensePolicyRules.duplicateKind(d("120"), d0, "MEALS", d("120"), d0.plusDays(4), "MEALS"));
        assertNull(ExpensePolicyRules.duplicateKind(d("120"), d0, "MEALS", d("121"), d0, "MEALS"));
        assertTrue(ExpensePolicyRules.duplicateFinding("SAME", "EXP-1").text().contains("duplicate of EXP-1"));
        assertFalse(ExpensePolicyRules.blocked(List.of(ExpensePolicyRules.duplicateFinding("NEAR", "EXP-1"))));
    }

    @Test
    void checksABatch() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        BatchClaim ok = new BatchClaim(a, "EXP-1", "APPROVED", d("100"));
        BatchClaim paid = new BatchClaim(b, "EXP-2", "REIMBURSED", d("50"));
        assertNull(ExpensePolicyRules.batchProblem(List.of(ok), Set.of(), 1));
        assertNotNull(ExpensePolicyRules.batchProblem(List.of(), Set.of(), 0));
        assertNotNull(ExpensePolicyRules.batchProblem(List.of(ok), Set.of(), 2));
        assertTrue(ExpensePolicyRules.batchProblem(List.of(ok, paid), Set.of(), 2).contains("EXP-2 is reimbursed"));
        assertTrue(ExpensePolicyRules.batchProblem(List.of(ok), Set.of(a), 1).contains("already in an unpaid batch"));
        assertNotNull(ExpensePolicyRules.batchProblem(List.of(ok), Set.of(), 201));
        assertEquals(d("150"), ExpensePolicyRules.total(List.of(ok, new BatchClaim(b, "EXP-2", "APPROVED", d("50")))));
    }

    @Test
    void checksPayingABatch() {
        UUID bank = UUID.randomUUID();
        assertNull(ExpensePolicyRules.payProblem("DRAFT", true, bank));
        assertNull(ExpensePolicyRules.payProblem("DRAFT", false, null));
        assertNotNull(ExpensePolicyRules.payProblem("DRAFT", true, null));
        assertNotNull(ExpensePolicyRules.payProblem("PAID", true, bank));
    }
}
