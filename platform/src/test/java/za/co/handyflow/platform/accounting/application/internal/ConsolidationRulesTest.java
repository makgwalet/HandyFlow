package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.ConsolidationRules.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static void eq(String exp, BigDecimal actual) { assertEquals(0, d(exp).compareTo(actual), "expected " + exp + " but was " + actual); }
    private static final UUID HQ = UUID.randomUUID(), SUB = UUID.randomUUID();
    private static final List<UUID> ENT = List.of(HQ, SUB);

    private static Row row(Result r, String code) { return r.rows().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow(); }

    /** HQ lent R100 to Sub. HQ: bank 400 (Dr), IC receivable 100 (Dr), equity 500 (Cr). Sub: bank 150 (Dr), IC payable 100 (Cr), equity 50 (Cr). Sub earned 0. */
    private static List<Bal> balances(String hqReceivable, String subPayable) {
        return List.of(
                new Bal(HQ, "1000", "Bank", "ASSET", d("400")), new Bal(HQ, "1900", "Loan to Sub", "ASSET", d(hqReceivable)), new Bal(HQ, "3000", "Capital", "EQUITY", d("-" + (400 + Integer.parseInt(hqReceivable)))),
                new Bal(SUB, "1000", "Bank", "ASSET", d("150")), new Bal(SUB, "2900", "Loan from HQ", "LIABILITY", d("-" + subPayable)), new Bal(SUB, "3000", "Capital", "EQUITY", d("-" + (150 - Integer.parseInt(subPayable)))));
    }
    private static final List<Elim> RULE = List.of(new Elim("HQ loan to Sub", HQ, "1900", SUB, "2900"));

    @Test
    void addsUpTheCompaniesAndBalances() {
        var r = ConsolidationRules.consolidate(ENT, balances("100", "100"), Map.of(), List.of());
        eq("550", row(r, "1000").consolidated());
        eq("650", r.assets());          // 400 + 150 + 100
        eq("0", r.balanceDifference());
    }

    @Test
    void eliminatesAMatchingIntercompanyPair() {
        var r = ConsolidationRules.consolidate(ENT, balances("100", "100"), Map.of(), RULE);
        eq("0", row(r, "1900").consolidated());
        eq("0", row(r, "2900").consolidated());
        eq("-100", row(r, "1900").elimination());
        eq("550", r.assets());
        eq("0", r.liabilities());
        eq("0", r.balanceDifference());
        eq("0", r.checks().get(0).difference());
    }

    @Test
    void showsTheDifferenceWhenTheSidesDisagree() {
        var r = ConsolidationRules.consolidate(ENT, balances("100", "90"), Map.of(), RULE);
        eq("10", r.checks().get(0).difference());
        eq("10", row(r, ConsolidationRules.DIFFERENCE_CODE).consolidated());
        eq("0", r.balanceDifference());
    }

    @Test
    void mapsDifferentChartsOntoOneGroupLine() {
        Map<Key, MapTo> map = Map.of(new Key(SUB, "1010"), new MapTo("1000", "Bank"));
        var bals = List.of(new Bal(HQ, "1000", "Bank", "ASSET", d("10")), new Bal(SUB, "1010", "Cheque account", "ASSET", d("5")),
                new Bal(HQ, "3000", "Capital", "EQUITY", d("-15")));
        var r = ConsolidationRules.consolidate(ENT, bals, map, List.of());
        assertEquals(1, r.rows().stream().filter(x -> x.type().equals("ASSET")).count());
        eq("15", row(r, "1000").consolidated());
        eq("5", row(r, "1000").amounts().get(1));
    }

    @Test
    void profitIsIncomeLessExpenses() {
        var bals = List.of(new Bal(HQ, "4000", "Sales", "INCOME", d("-1000")), new Bal(HQ, "5100", "Costs", "EXPENSE", d("400")),
                new Bal(SUB, "4000", "Sales", "INCOME", d("-200")), new Bal(HQ, "1000", "Bank", "ASSET", d("600")), new Bal(SUB, "1000", "Bank", "ASSET", d("200")));
        var r = ConsolidationRules.consolidate(ENT, bals, Map.of(), List.of());
        eq("1200", r.income());
        eq("400", r.expenses());
        eq("800", r.profit());
        eq("0", r.balanceDifference());
    }

    @Test
    void intercompanySalesCancelOutOfIncomeAndExpenses() {
        // Sub sold R300 of services to HQ.
        var bals = List.of(new Bal(SUB, "4100", "IC revenue", "INCOME", d("-300")), new Bal(HQ, "5900", "IC cost", "EXPENSE", d("300")),
                new Bal(HQ, "1000", "Bank", "ASSET", d("-300")), new Bal(SUB, "1000", "Bank", "ASSET", d("300")));
        var rule = List.of(new Elim("IC services", SUB, "4100", HQ, "5900"));
        var r = ConsolidationRules.consolidate(ENT, bals, Map.of(), rule);
        eq("0", r.income());
        eq("0", r.expenses());
        eq("0", r.profit());
        eq("0", r.balanceDifference());
    }

    @Test
    void ignoresCompaniesOutsideTheGroup() {
        var r = ConsolidationRules.consolidate(ENT, List.of(new Bal(UUID.randomUUID(), "1000", "Bank", "ASSET", d("999"))), Map.of(), List.of());
        assertTrue(r.rows().isEmpty());
    }

    @Test
    void validatesRulesMappingsAndInvites() {
        assertNull(ConsolidationRules.eliminationProblem("Loan", HQ, "1900", SUB, "2900", ENT, List.of()));
        assertNotNull(ConsolidationRules.eliminationProblem("", HQ, "1900", SUB, "2900", ENT, List.of()));
        assertNotNull(ConsolidationRules.eliminationProblem("x", HQ, "1900", HQ, "2900", ENT, List.of()));
        assertNotNull(ConsolidationRules.eliminationProblem("x", HQ, "1900", UUID.randomUUID(), "2900", ENT, List.of()));
        assertNotNull(ConsolidationRules.eliminationProblem("x", HQ, "1900", SUB, "2901", ENT, RULE), "account already in a rule");
        assertNull(ConsolidationRules.mappingProblem("1010", "1000"));
        assertNotNull(ConsolidationRules.mappingProblem("1010", "IC-DIFF"));
        assertNotNull(ConsolidationRules.mappingProblem("", "1000"));
        Instant now = Instant.parse("2026-10-14T10:00:00Z");
        assertNull(ConsolidationRules.inviteProblem(now.plusSeconds(60), null, now));
        assertNotNull(ConsolidationRules.inviteProblem(now.minusSeconds(60), null, now));
        assertNotNull(ConsolidationRules.inviteProblem(now.plusSeconds(60), now, now));
        assertNotNull(ConsolidationRules.joinProblem(HQ, HQ, false));
        assertNotNull(ConsolidationRules.joinProblem(HQ, SUB, true));
        assertNull(ConsolidationRules.joinProblem(HQ, SUB, false));
        assertNotNull(ConsolidationRules.ownershipProblem(d("0")));
        assertNull(ConsolidationRules.ownershipProblem(d("100")));
        assertEquals(List.of(SUB), ConsolidationRules.partlyOwned(Map.of(HQ, d("100"), SUB, d("60"))));
    }
}
