package za.co.handyflow.platform.accounting.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import za.co.handyflow.platform.accounting.application.internal.BankReconRules.Line;
import za.co.handyflow.platform.accounting.application.internal.BankReconRules.Rule;

/** Finance patch F12: bank reconciliation difference, sign-off and matching rules. */
class BankReconRulesTest {
    static BigDecimal d(String v) { return new BigDecimal(v); }
    UUID acc = UUID.randomUUID(), other = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID(), target = UUID.randomUUID();

    @Test void differenceShowsWhatIsUnexplained() {
        assertEquals(d("0.00"), BankReconRules.difference(d("1000"), d("1000"), d("0"), d("0")));
        assertEquals(d("50.00"), BankReconRules.difference(d("1000"), d("1050"), d("0"), d("0")));
        assertEquals(d("-50.00"), BankReconRules.difference(d("1050"), d("1000"), d("0"), d("0")));
        // a cheque in the books not yet on the statement (-500 in books): books 500, statement 1000
        assertEquals(d("0.00"), BankReconRules.difference(d("1000"), d("500"), d("0"), d("-500")));
        // a bank fee on the statement not yet booked: statement 900, books 1000
        assertEquals(d("0.00"), BankReconRules.difference(d("900"), d("1000"), d("-100"), d("0")));
    }
    @Test void balancedMeansToTheCent() {
        assertTrue(BankReconRules.balanced(d("0")));
        assertTrue(BankReconRules.balanced(d("0.004")));
        assertFalse(BankReconRules.balanced(d("0.01")));
        assertFalse(BankReconRules.balanced(d("-0.01")));
    }
    @Test void signOffNeedsEveryLineMatchedAndNoDifference() {
        assertNull(BankReconRules.signOffProblem(0, d("0"), bob, Set.of(alice), 1));
        assertTrue(BankReconRules.signOffProblem(1, d("0"), bob, Set.of(), 1).startsWith("1 statement line is not matched"));
        assertTrue(BankReconRules.signOffProblem(3, d("0"), bob, Set.of(), 1).startsWith("3 statement lines are not matched"));
        assertTrue(BankReconRules.signOffProblem(0, d("25.50"), bob, Set.of(), 1).contains("R 25.50 apart (the ledger is higher)"));
        assertTrue(BankReconRules.signOffProblem(0, d("-25.50"), bob, Set.of(), 1).contains("the statement is higher"));
    }
    @Test void signerIsNotTheMakerWhenSomeoneElseCanSign() {
        String p = BankReconRules.signOffProblem(0, d("0"), alice, Set.of(alice), 1);
        assertTrue(p.contains("someone else must sign it off") && p.contains("one other person can"));
        assertTrue(BankReconRules.signOffProblem(0, d("0"), alice, Set.of(alice, bob), 3).contains("3 other people can"));
        assertNull(BankReconRules.signOffProblem(0, d("0"), alice, Set.of(alice), 0));
        assertTrue(BankReconRules.soleSigner(alice, Set.of(alice), 0));
        assertFalse(BankReconRules.soleSigner(alice, Set.of(alice), 1));
        assertFalse(BankReconRules.soleSigner(alice, Set.of(bob), 0));
    }
    @Test void periodMustBePastAndAfterTheLastSignOff() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        assertNull(BankReconRules.periodProblem(LocalDate.of(2026, 9, 30), today, null));
        assertNull(BankReconRules.periodProblem(today, today, LocalDate.of(2026, 8, 31)));
        assertNotNull(BankReconRules.periodProblem(null, today, null));
        assertTrue(BankReconRules.periodProblem(today.plusDays(1), today, null).contains("future"));
        assertTrue(BankReconRules.periodProblem(LocalDate.of(2026, 8, 31), today, LocalDate.of(2026, 8, 31)).contains("already signed off through 2026-08-31"));
    }
    @Test void signedOffPeriodsRefuseNewLines() {
        LocalDate through = LocalDate.of(2026, 8, 31);
        assertNull(BankReconRules.signedOffLineProblem(LocalDate.of(2026, 9, 1), through));
        assertNull(BankReconRules.signedOffLineProblem(LocalDate.of(2026, 8, 1), null));
        assertTrue(BankReconRules.signedOffLineProblem(through, through).contains("signed off through 2026-08-31"));
    }

    Rule rule(String name, String dir, String text, String field, String min, String max, int prio, boolean active, UUID bank) {
        return new Rule(UUID.randomUUID(), name, bank, dir, text, field, min == null ? null : d(min), max == null ? null : d(max), prio, active, target);
    }
    Line line(String type, String desc, String ref, String amt) { return new Line(acc, type, desc, ref, d(amt)); }

    @Test void ruleNeedsAConditionAndAnAccount() {
        assertNull(BankReconRules.ruleProblem("Bank fees", "OUT", "SERVICE FEE", null, null, target));
        assertNull(BankReconRules.ruleProblem("Small", "ANY", null, d("0"), d("50"), target));
        assertNotNull(BankReconRules.ruleProblem(" ", "OUT", "x", null, null, target));
        assertNotNull(BankReconRules.ruleProblem("n", "OUT", "x", null, null, null));
        assertNotNull(BankReconRules.ruleProblem("n", "SIDEWAYS", "x", null, null, target));
        assertTrue(BankReconRules.ruleProblem("n", "ANY", "  ", null, null, target).contains("every line"));
        assertNotNull(BankReconRules.ruleProblem("n", "ANY", "x", d("10"), d("5"), target));
        assertNotNull(BankReconRules.ruleProblem("n", "ANY", "x", d("-1"), null, target));
    }
    @Test void textMatchesAnyCaseAndSpacing() {
        Rule r = rule("Fees", "OUT", "service  fee", "EITHER", null, null, 100, true, null);
        assertTrue(BankReconRules.matches(r, line("DEBIT", "MONTHLY Service Fee", "", "99")));
        assertTrue(BankReconRules.matches(r, line("DEBIT", "x", "SERVICE FEE JUN", "99")));
        assertFalse(BankReconRules.matches(r, line("DEBIT", "Interest", "", "99")));
    }
    @Test void directionAndFieldAndAmountAreRespected() {
        Rule in = rule("Interest", "IN", "interest", "DESCRIPTION", null, null, 100, true, null);
        assertTrue(BankReconRules.matches(in, line("CREDIT", "Interest paid", "", "5")));
        assertFalse(BankReconRules.matches(in, line("DEBIT", "Interest charged", "", "5")));
        assertFalse(BankReconRules.matches(in, line("CREDIT", "x", "interest", "5"))); // reference only, rule wants description
        Rule ranged = rule("Small fees", "OUT", "fee", "EITHER", "10", "100", 100, true, null);
        assertTrue(BankReconRules.matches(ranged, line("DEBIT", "fee", "", "10.00")));
        assertTrue(BankReconRules.matches(ranged, line("DEBIT", "fee", "", "100")));
        assertFalse(BankReconRules.matches(ranged, line("DEBIT", "fee", "", "9.99")));
        assertFalse(BankReconRules.matches(ranged, line("DEBIT", "fee", "", "100.01")));
        Rule amountOnly = rule("Round", "ANY", null, "EITHER", "1000", null, 100, true, null);
        assertTrue(BankReconRules.matches(amountOnly, line("CREDIT", "anything", "", "1500")));
    }
    @Test void ruleForAnotherAccountOrInactiveNeverMatches() {
        Rule other_ = rule("Other acct", "ANY", "fee", "EITHER", null, null, 100, true, other);
        Rule off = rule("Off", "ANY", "fee", "EITHER", null, null, 100, false, null);
        assertFalse(BankReconRules.matches(other_, line("DEBIT", "fee", "", "5")));
        assertFalse(BankReconRules.matches(off, line("DEBIT", "fee", "", "5")));
        assertFalse(BankReconRules.matches(null, line("DEBIT", "fee", "", "5")));
    }
    @Test void firstMatchPrefersPriorityThenTheMoreSpecificText() {
        Rule generic = rule("Fees", "OUT", "fee", "EITHER", null, null, 100, true, null);
        Rule specific = rule("Card fee", "OUT", "card fee", "EITHER", null, null, 100, true, null);
        Rule urgent = rule("Urgent", "OUT", "fee", "EITHER", null, null, 10, true, null);
        Line l = line("DEBIT", "Monthly card fee", "", "50");
        assertSame(specific, BankReconRules.firstMatch(List.of(generic, specific), l));
        assertSame(urgent, BankReconRules.firstMatch(List.of(generic, specific, urgent), l));
        assertNull(BankReconRules.firstMatch(List.of(generic), line("DEBIT", "Salary", "", "50")));
        assertNull(BankReconRules.firstMatch(List.of(), l));
    }
}
