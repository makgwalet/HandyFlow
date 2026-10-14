package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.TagRules.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TagRulesTest {
    private static final UUID SITE_A = UUID.randomUUID(), SITE_B = UUID.randomUUID(), CAR = UUID.randomUUID();

    private static Rule rule(String name, int prio, String acct, String text, String type, UUID cc) { return new Rule(UUID.randomUUID(), name, prio, acct, text, type, cc); }
    private static Line line(String acct, String lineDesc, String entryDesc, String ref, String type) { return new Line(acct, "EXPENSE", lineDesc, entryDesc, ref, type, null); }

    @Test
    void aRuleNeedsAConditionAndATarget() {
        assertNull(TagRules.saveProblem("Fuel for CA 123", "5140", null, null, CAR, 10));
        assertNotNull(TagRules.saveProblem("", "5140", null, null, CAR, 10));
        assertNotNull(TagRules.saveProblem("x", null, " ", null, CAR, 10), "no condition");
        assertNotNull(TagRules.saveProblem("x", null, "ab", null, CAR, 10), "words too short");
        assertNotNull(TagRules.saveProblem("x", "5140", null, "MAGIC", CAR, 10));
        assertNotNull(TagRules.saveProblem("x", "5140", null, null, null, 10));
        assertNotNull(TagRules.saveProblem("x", "5140", null, null, CAR, 0));
    }

    @Test
    void matchesOnAccountWordsAndType() {
        Rule fuel = rule("Fuel", 10, "5140", "CA 123", null, CAR);
        assertTrue(TagRules.matches(fuel, line("5140", "Diesel for ca 123", "Fuel", "REF", "MANUAL")));
        assertFalse(TagRules.matches(fuel, line("5150", "Diesel for CA 123", "Fuel", "REF", "MANUAL")), "wrong account");
        assertFalse(TagRules.matches(fuel, line("5140", "Diesel for CA 999", "Fuel", "REF", "MANUAL")), "wrong words");
        assertTrue(TagRules.matches(rule("Site A", 1, null, "sandton mall", null, SITE_A), line("5110", null, "Guards at Sandton Mall June", null, "MANUAL")), "journal text counts");
        assertTrue(TagRules.matches(rule("Dep", 1, null, null, "DEPRECIATION", SITE_A), line("5220", null, null, null, "DEPRECIATION")));
        assertFalse(TagRules.matches(rule("Dep", 1, null, null, "DEPRECIATION", SITE_A), line("5220", null, null, null, "MANUAL")));
    }

    @Test
    void lowestPriorityNumberWins() {
        List<Rule> rules = List.of(rule("General fuel", 50, "5140", null, null, SITE_B), rule("This car", 5, "5140", "CA 123", null, CAR));
        assertEquals(CAR, TagRules.pick(rules, line("5140", "Diesel CA 123", null, null, "MANUAL")).costCentreId());
        assertEquals(SITE_B, TagRules.pick(rules, line("5140", "Diesel CA 999", null, null, "MANUAL")).costCentreId());
        assertNull(TagRules.pick(rules, line("5110", "Wages", null, null, "MANUAL")));
    }

    @Test
    void leavesTaggedAndBalanceSheetLinesAlone() {
        List<Rule> rules = List.of(rule("Any", 1, "1000", null, null, CAR), rule("Fuel", 2, "5140", null, null, CAR));
        assertNull(TagRules.pick(rules, new Line("1000", "ASSET", "x", null, null, "MANUAL", null)), "balance sheet");
        assertNull(TagRules.pick(rules, new Line("5140", "EXPENSE", "x", null, null, "MANUAL", SITE_A)), "already tagged");
        assertNotNull(TagRules.pick(rules, new Line("5140", "EXPENSE", "x", null, null, "MANUAL", null)));
    }
}
