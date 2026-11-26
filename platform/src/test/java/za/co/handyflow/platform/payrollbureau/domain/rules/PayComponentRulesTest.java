package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.payrollbureau.domain.rules.PayComponentRules.Line;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayComponentRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void linesAreTotalledByTreatment() {
        var t = PayComponentRules.summarise(List.of(
                new Line("TAXABLE_EARNING", d("1200.50")), new Line("TAXABLE_EARNING", d("300")),
                new Line("BONUS", d("5000")), new Line("NON_TAXABLE_EARNING", d("250")),
                new Line("DEDUCTION", d("400")), new Line("DEDUCTION", d("99.99"))));
        assertEquals(0, d("1500.50").compareTo(t.taxable()));
        assertEquals(0, d("5000").compareTo(t.bonus()));
        assertEquals(0, d("250").compareTo(t.nonTaxable()));
        assertEquals(0, d("499.99").compareTo(t.deductions()));
        assertEquals(0, d("6750.50").compareTo(t.extraEarnings()));
    }

    @Test
    void noLinesGivesZeroes() {
        var t = PayComponentRules.summarise(List.of());
        assertEquals(0, t.extraEarnings().signum());
        assertEquals(0, t.deductions().signum());
    }

    @Test
    void anUnknownRoleIsRefusedRatherThanIgnored() {
        assertThrows(IllegalArgumentException.class, () -> PayComponentRules.summarise(List.of(new Line("MYSTERY", d("1")))));
    }

    @Test
    void componentsMustHaveACodeANameAndAKnownRole() {
        assertNull(PayComponentRules.componentProblem("SHIFT_ALLOW", "Shift allowance", "TAXABLE_EARNING", "3601"));
        assertNull(PayComponentRules.componentProblem("UNION", "Union fees", "DEDUCTION", null));
        assertNotNull(PayComponentRules.componentProblem("", "x", "DEDUCTION", null));
        assertNotNull(PayComponentRules.componentProblem("A B", "x", "DEDUCTION", null));
        assertNotNull(PayComponentRules.componentProblem("A", " ", "DEDUCTION", null));
        assertNotNull(PayComponentRules.componentProblem("A", "x", "FRINGE", null));
        assertNotNull(PayComponentRules.componentProblem("A", "x", null, null));
        assertNotNull(PayComponentRules.componentProblem("A", "x", "BONUS", "36O5"));
    }

    @Test
    void amountsMustBePositiveReasonableAndInCents() {
        assertNull(PayComponentRules.amountProblem(d("0.01")));
        assertNotNull(PayComponentRules.amountProblem(d("0")));
        assertNotNull(PayComponentRules.amountProblem(d("-5")));
        assertNotNull(PayComponentRules.amountProblem(null));
        assertNotNull(PayComponentRules.amountProblem(d("10000000")));
        assertNotNull(PayComponentRules.amountProblem(d("1.005")));
    }

    @Test
    void theStandardSetHasUniqueCodesAndValidRoles() {
        var seen = new java.util.HashSet<String>();
        for (var s : PayComponentRules.STANDARD) {
            assertTrue(seen.add(s.code()), "duplicate " + s.code());
            assertTrue(PayComponentRules.componentProblem(s.code(), s.name(), s.role(), s.sarsCode()) == null, s.code());
        }
        assertTrue(PayComponentRules.earning("BONUS"));
        assertFalse(PayComponentRules.earning("DEDUCTION"));
    }
}
