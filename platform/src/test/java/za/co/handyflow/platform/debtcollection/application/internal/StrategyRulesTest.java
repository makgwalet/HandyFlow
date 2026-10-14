package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.StrategyRules.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StrategyRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static Step step(int days, String action) { return new Step(UUID.randomUUID(), days, action, null); }
    private static Strategy strat(String name, int prio, String min, String max, Integer minD, Integer maxD, Step... steps) {
        return new Strategy(UUID.randomUUID(), name, prio, min == null ? null : d(min), max == null ? null : d(max), minD, maxD, 3, List.of(steps));
    }

    @Test
    void aStrategyNeedsNamedOrderedStepsOnDifferentDays() {
        assertNull(StrategyRules.problem(strat("Small", 1, null, "5000", null, null, step(7, "SEND_REMINDER"), step(30, "CALL"))));
        assertNotNull(StrategyRules.problem(strat(" ", 1, null, null, null, null, step(7, "CALL"))));
        assertNotNull(StrategyRules.problem(strat("A", 1, null, null, null, null)));
        assertNotNull(StrategyRules.problem(strat("A", 1, null, null, null, null, step(7, "CALL"), step(7, "SEND_REMINDER"))));
        assertNotNull(StrategyRules.problem(strat("A", 1, null, null, null, null, step(7, "DANCE"))));
        assertNotNull(StrategyRules.problem(strat("A", 1, "100", "50", null, null, step(7, "CALL"))));
        assertNotNull(StrategyRules.problem(strat("A", 1, null, null, 30, 10, step(7, "CALL"))));
        assertNotNull(StrategyRules.problem(strat("A", 0, null, null, null, null, step(7, "CALL"))));
        assertNotNull(StrategyRules.problem(strat("A", 1, null, null, null, null, step(-1, "CALL"))));
    }

    @Test
    void theFirstMatchingStrategyInOrderApplies() {
        Strategy big = strat("Large balances", 1, "50000", null, null, null, step(3, "CALL"));
        Strategy general = strat("Everyone", 9, null, null, null, null, step(7, "SEND_REMINDER"));
        Strategy old = strat("Very old small", 2, null, "5000", 90, null, step(1, "REVIEW_WRITE_OFF"));
        List<Strategy> all = List.of(general, old, big);
        assertEquals("Large balances", StrategyRules.pick(all, d("60000"), 10).name());
        assertEquals("Very old small", StrategyRules.pick(all, d("2000"), 120).name());
        assertEquals("Everyone", StrategyRules.pick(all, d("2000"), 20).name());
        assertNull(StrategyRules.pick(List.of(big), d("100"), 5));
    }

    @Test
    void casesInTroubleOrRecentlyContactedAreLeftAlone() {
        assertNull(StrategyRules.skipReason("OPEN", false, 10L, 3));
        assertNull(StrategyRules.skipReason("DEMAND_SENT", false, null, 3));
        assertNotNull(StrategyRules.skipReason("DISPUTED", false, null, 3));
        assertNotNull(StrategyRules.skipReason("PAYMENT_PLAN_ACTIVE", false, null, 3));
        assertNotNull(StrategyRules.skipReason("HANDED_TO_LEGAL", false, null, 3));
        assertTrue(StrategyRules.skipReason("OPEN", true, null, 3).contains("promise"));
        assertTrue(StrategyRules.skipReason("OPEN", false, 1L, 3).contains("1 day"));
        assertNull(StrategyRules.skipReason("OPEN", false, 1L, 0));
    }

    @Test
    void onlyTheLatestDueStepIsRaisedAndEarlierOnesAreSkipped() {
        Step a = step(7, "SEND_REMINDER"), b = step(30, "CALL"), c = step(60, "SEND_DEMAND");
        var steps = List.of(c, a, b);
        var none = StrategyRules.decide(steps, 3, Set.of());
        assertNull(none.raise());
        var first = StrategyRules.decide(steps, 8, Set.of());
        assertEquals(a, first.raise());
        assertTrue(first.skipped().isEmpty());
        var late = StrategyRules.decide(steps, 45, Set.of());
        assertEquals(b, late.raise());
        assertEquals(List.of(a), late.skipped());
        var next = StrategyRules.decide(steps, 70, Set.of(a.id(), b.id()));
        assertEquals(c, next.raise());
        assertTrue(next.skipped().isEmpty());
        assertNull(StrategyRules.decide(steps, 70, Set.of(a.id(), b.id(), c.id())).raise());
    }

    @Test
    void theWorkItemTitleSaysWhatToDoAndWhy() {
        Step s = new Step(UUID.randomUUID(), 30, "CALL", "Offer a payment plan");
        assertEquals("Call: DC-001, Acme (32 days overdue). Offer a payment plan", StrategyRules.title(s, "DC-001", "Acme", 32));
        assertTrue(StrategyRules.title(new Step(UUID.randomUUID(), 90, "LEGAL_REVIEW", null), "DC-2", "B", 95).startsWith("Review for legal action"));
        assertTrue(StrategyRules.isLegal(new Step(UUID.randomUUID(), 1, "LEGAL_REVIEW", null)));
    }
}
