package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.payrollbureau.domain.rules.RunReviewRules.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RunReviewRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static Facts emp(String name, String gross, String id, String tax, String bank, String email) {
        return new Facts(UUID.randomUUID(), name, d(gross), id, tax, bank, email);
    }

    private static long count(List<Finding> f, String code) { return f.stream().filter(x -> x.code().equals(code)).count(); }

    @Test
    void aCompleteEmployeeRaisesNothing() {
        var f = RunReviewRules.preflight(List.of(emp("Ann", "20000", "9001015009087", "123", "62000001", "a@x.co.za")), List.of());
        assertTrue(f.isEmpty());
        assertFalse(RunReviewRules.blocked(f));
    }

    @Test
    void anEmptyRunAndZeroPayBlockTheRun() {
        assertTrue(RunReviewRules.blocked(RunReviewRules.preflight(List.of(), List.of())));
        var f = RunReviewRules.preflight(List.of(emp("Bob", "0", "1", "2", "3", "b@x")), List.of());
        assertEquals(1, count(f, "ZERO_PAY"));
        assertTrue(RunReviewRules.blocked(f));
    }

    @Test
    void missingDetailsWarnButDoNotBlock() {
        var f = RunReviewRules.preflight(List.of(emp("Cy", "15000", "1", null, " ", "")), List.of());
        assertEquals(1, count(f, "NO_BANK_ACCOUNT"));
        assertEquals(1, count(f, "NO_TAX_NUMBER"));
        assertEquals(1, count(f, "NO_EMAIL"));
        assertFalse(RunReviewRules.blocked(f));
    }

    @Test
    void sharedIdNumbersAndBankAccountsAreFlaggedOnBothEmployees() {
        var f = RunReviewRules.preflight(List.of(
                emp("Di", "10000", "9001015009087", "1", "555", "d@x"),
                emp("Ed", "10000", "9001015009087", "2", "555", "e@x")), List.of());
        assertEquals(2, count(f, "DUPLICATE_ID_NUMBER"));
        assertEquals(2, count(f, "DUPLICATE_BANK_ACCOUNT"));
        assertFalse(RunReviewRules.blocked(f));
    }

    @Test
    void anOverlappingRunBlocks() {
        var f = RunReviewRules.preflight(List.of(emp("Fay", "10000", "1", "1", "1", "f@x")), List.of("PR-0007"));
        assertEquals(1, count(f, "OVERLAPPING_RUN"));
        assertTrue(RunReviewRules.blocked(f));
    }

    @Test
    void varianceFlagsNewLeftAndBigNetChanges() {
        UUID same = UUID.randomUUID(), big = UUID.randomUUID(), gone = UUID.randomUUID(), fresh = UUID.randomUUID();
        Map<UUID, Pay> before = new HashMap<>(), now = new HashMap<>();
        before.put(same, new Pay("Gus", d("10000"))); now.put(same, new Pay("Gus", d("10500")));   // 5%: quiet
        before.put(big, new Pay("Hal", d("10000")));  now.put(big, new Pay("Hal", d("11000")));    // exactly 10%: flagged
        before.put(gone, new Pay("Ivy", d("8000")));
        now.put(fresh, new Pay("Jon", d("9000")));
        var f = RunReviewRules.variance(before, now);
        assertEquals(1, count(f, "NET_PAY_CHANGED"));
        assertEquals(1, count(f, "NOT_IN_THIS_RUN"));
        assertEquals(1, count(f, "NEW_IN_RUN"));
        assertEquals(3, f.size());
    }

    @Test
    void anIdenticalRunHasNoVariance() {
        UUID a = UUID.randomUUID();
        var m = Map.of(a, new Pay("Kim", d("12345.67")));
        assertTrue(RunReviewRules.variance(m, m).isEmpty());
    }
}
