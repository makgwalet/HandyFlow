package za.co.handyflow.platform.bookkeeping.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.bookkeeping.application.internal.BkWorkRules.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BkWorkRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final UUID BANK = UUID.randomUUID(), CONTRA = UUID.randomUUID();
    private static Rule rule(String name, String dir, String text, String min, String max, int prio) {
        return new Rule(UUID.randomUUID(), name, null, dir, text, "EITHER", min == null ? null : d(min), max == null ? null : d(max), prio, true, CONTRA);
    }
    private static Line out(String desc, String amt) { return new Line(BANK, "DEBIT", desc, null, d(amt)); }

    @Test
    void ruleNeedsSomethingToMatch() {
        assertNull(BkWorkRules.ruleProblem("Fuel", "OUT", "engen", null, null, CONTRA));
        assertNull(BkWorkRules.ruleProblem("Small", null, null, d("0"), d("50"), CONTRA));
        assertNotNull(BkWorkRules.ruleProblem(" ", "OUT", "engen", null, null, CONTRA));
        assertNotNull(BkWorkRules.ruleProblem("Fuel", "OUT", "engen", null, null, null));
        assertNotNull(BkWorkRules.ruleProblem("All", "OUT", " ", null, null, CONTRA));
        assertNotNull(BkWorkRules.ruleProblem("Fuel", "SIDEWAYS", "engen", null, null, CONTRA));
        assertNotNull(BkWorkRules.ruleProblem("Fuel", "OUT", "engen", d("9"), d("1"), CONTRA));
    }

    @Test
    void matchesTextDirectionAndAmount() {
        Rule r = rule("Fuel", "OUT", "ENGEN", "100", "2000", 10);
        assertTrue(BkWorkRules.matches(r, out("Card purchase  engen  Sandton", "850")));
        assertFalse(BkWorkRules.matches(r, new Line(BANK, "CREDIT", "engen refund", null, d("850"))));
        assertFalse(BkWorkRules.matches(r, out("Engen", "50")));
        assertFalse(BkWorkRules.matches(r, out("Shell", "850")));
        assertTrue(BkWorkRules.matches(rule("Ref", "ANY", "inv-7", null, null, 10), new Line(BANK, "DEBIT", "x", "INV-7", d("1"))));
    }

    @Test
    void inactiveOrOtherAccountRulesDoNotMatch() {
        Rule off = new Rule(UUID.randomUUID(), "Off", null, "ANY", "engen", "EITHER", null, null, 1, false, CONTRA);
        assertFalse(BkWorkRules.matches(off, out("engen", "10")));
        Rule other = new Rule(UUID.randomUUID(), "Other", UUID.randomUUID(), "ANY", "engen", "EITHER", null, null, 1, true, CONTRA);
        assertFalse(BkWorkRules.matches(other, out("engen", "10")));
    }

    @Test
    void theMoreSpecificRuleWins() {
        Rule general = rule("Any card", "OUT", "card", null, null, 10);
        Rule specific = rule("Engen", "OUT", "card purchase engen", null, null, 10);
        Rule urgent = rule("Priority", "OUT", "card", null, null, 1);
        Line l = out("card purchase engen", "10");
        assertSame(specific, BkWorkRules.firstMatch(List.of(general, specific), l));
        assertSame(urgent, BkWorkRules.firstMatch(List.of(general, specific, urgent), l));
        assertNull(BkWorkRules.firstMatch(List.of(general), out("eft", "10")));
    }

    @Test
    void limitsBulkWork() {
        assertNotNull(BkWorkRules.bulkProblem(0));
        assertNull(BkWorkRules.bulkProblem(200));
        assertNotNull(BkWorkRules.bulkProblem(201));
    }

    @Test
    void checksQueries() {
        assertNull(BkWorkRules.queryProblem("MISSING_DOCUMENT", "Need the invoice for this"));
        assertNotNull(BkWorkRules.queryProblem("OTHER", "Need the invoice for this"));
        assertNotNull(BkWorkRules.queryProblem("QUESTION", "hm"));
        assertNull(BkWorkRules.answerProblem("OPEN", "It was the roof repair", null));
        assertNull(BkWorkRules.answerProblem("OPEN", null, "drive/inv-12.pdf"));
        assertNotNull(BkWorkRules.answerProblem("OPEN", " ", null));
        assertNotNull(BkWorkRules.answerProblem("CLOSED", "x yes", null));
        assertNotNull(BkWorkRules.closeProblem("CLOSED"));
        assertNull(BkWorkRules.closeProblem("ANSWERED"));
    }

    @Test
    void handoverNeedsACleanPeriod() {
        LocalDate today = LocalDate.of(2026, 10, 14), end = LocalDate.of(2026, 9, 30);
        assertNull(BkWorkRules.handoverProblem(end, today, 0, 0, false));
        assertNotNull(BkWorkRules.handoverProblem(null, today, 0, 0, false));
        assertNotNull(BkWorkRules.handoverProblem(today.plusDays(1), today, 0, 0, false));
        assertNotNull(BkWorkRules.handoverProblem(end, today, 0, 0, true));
        assertTrue(BkWorkRules.handoverProblem(end, today, 3, 0, false).contains("3 bank lines are not categorised"));
        assertTrue(BkWorkRules.handoverProblem(end, today, 0, 1, false).contains("1 journal is still a draft"));
        assertEquals(1, BkWorkRules.handoverWarnings(2).size());
        assertTrue(BkWorkRules.handoverWarnings(0).isEmpty());
    }

    @Test
    void anotherPersonRespondsToAHandover() {
        UUID sender = UUID.randomUUID(), other = UUID.randomUUID();
        assertNull(BkWorkRules.respondProblem("SENT", "ACCEPT", null, sender, other, 1));
        assertNotNull(BkWorkRules.respondProblem("SENT", "ACCEPT", null, sender, sender, 1));
        assertNull(BkWorkRules.respondProblem("SENT", "ACCEPT", null, sender, sender, 0));
        assertTrue(BkWorkRules.soleResponder(sender, sender, 0));
        assertNotNull(BkWorkRules.respondProblem("SENT", "RETURN", "no", sender, other, 1));
        assertNull(BkWorkRules.respondProblem("SENT", "RETURN", "Bank rec is out by R 200", sender, other, 1));
        assertNotNull(BkWorkRules.respondProblem("ACCEPTED", "ACCEPT", null, sender, other, 1));
        assertNotNull(BkWorkRules.respondProblem("SENT", "MAYBE", null, sender, other, 1));
    }
}
