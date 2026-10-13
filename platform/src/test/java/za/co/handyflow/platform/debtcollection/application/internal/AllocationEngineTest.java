package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.CaseFacts;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.Collector;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.Decision;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.RuleView;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static UUID id(int n) { return UUID.fromString("00000000-0000-0000-0000-0000000000" + String.format("%02d", n)); }

    private static Collector col(int n, String name, CollectorTier tier, String team, Set<String> skills) {
        return new Collector(id(n), name, team, tier, skills, null, null, null, null, true, 0, BigDecimal.ZERO, 0L);
    }

    private static CaseFacts kase(int n, String balance, CaseStatus status, long age) {
        return new CaseFacts(id(100 + n), "DC-" + String.format("%05d", n), "Debtor " + n, d(balance), status, age, null, null);
    }

    private static Map<UUID, Long> counts(List<Decision> ds) {
        Map<UUID, Long> m = new HashMap<>();
        ds.stream().filter(Decision::allocated).forEach(x -> m.merge(x.toUserId(), 1L, Long::sum));
        return m;
    }

    private static RuleView rule(String name, int order, String minBal, String team, String skill, CollectorTier tier) {
        return new RuleView(UUID.randomUUID(), name, order, minBal == null ? null : d(minBal), null, null, null, Set.of(),
                team, skill, tier, null);
    }

    @Test
    void roundRobinRotatesEvenly() {
        List<Collector> cs = List.of(col(1, "A", CollectorTier.STANDARD, null, Set.of()), col(2, "B", CollectorTier.STANDARD, null, Set.of()),
                col(3, "C", CollectorTier.STANDARD, null, Set.of()));
        List<CaseFacts> cases = new ArrayList<>();
        for (int i = 1; i <= 6; i++) cases.add(kase(i, "1000", CaseStatus.OPEN, 10));
        var counts = counts(AllocationEngine.allocateBatch(cases, cs, List.of(), AllocationStrategy.ROUND_ROBIN, TODAY));
        assertEquals(2L, counts.get(id(1)));
        assertEquals(2L, counts.get(id(2)));
        assertEquals(2L, counts.get(id(3)));
    }

    @Test
    void roundRobinStartsWithTheCollectorWhoWasAssignedLongestAgo() {
        Collector recent = col(1, "Recent", CollectorTier.STANDARD, null, Set.of());
        recent.lastAssigned = 500L;
        Collector idle = col(2, "Idle", CollectorTier.STANDARD, null, Set.of());
        idle.lastAssigned = 100L;
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "1000", CaseStatus.OPEN, 1)), List.of(recent, idle), List.of(),
                AllocationStrategy.ROUND_ROBIN, TODAY);
        assertEquals(id(2), out.get(0).toUserId());
    }

    @Test
    void leastLoadedPicksTheCollectorWithFewestCases() {
        Collector busy = col(1, "Busy", CollectorTier.STANDARD, null, Set.of());
        busy.openCases = 9;
        Collector light = col(2, "Light", CollectorTier.STANDARD, null, Set.of());
        light.openCases = 2;
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "1000", CaseStatus.OPEN, 1)), List.of(busy, light), List.of(),
                AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals(id(2), out.get(0).toUserId());
    }

    @Test
    void leastValueBalancesMoneyNotHeadcount() {
        Collector manySmall = col(1, "ManySmall", CollectorTier.STANDARD, null, Set.of());
        manySmall.openCases = 30;
        manySmall.portfolio = d("30000");
        Collector fewBig = col(2, "FewBig", CollectorTier.STANDARD, null, Set.of());
        fewBig.openCases = 3;
        fewBig.portfolio = d("900000");
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "5000", CaseStatus.OPEN, 1)), List.of(manySmall, fewBig), List.of(),
                AllocationStrategy.LEAST_VALUE, TODAY);
        assertEquals(id(1), out.get(0).toUserId());
    }

    @Test
    void unavailableCollectorsAreSkipped() {
        Collector inactive = new Collector(id(1), "Inactive", null, CollectorTier.STANDARD, Set.of(), null, null, null, null, false, 0, BigDecimal.ZERO, 0L);
        Collector onLeave = new Collector(id(2), "OnLeave", null, CollectorTier.STANDARD, Set.of(), null, null, null, TODAY, true, 0, BigDecimal.ZERO, 0L);
        Collector back = new Collector(id(3), "Back", null, CollectorTier.STANDARD, Set.of(), null, null, null, TODAY.minusDays(1), true, 0, BigDecimal.ZERO, 0L);
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "1000", CaseStatus.OPEN, 1)), List.of(inactive, onLeave, back), List.of(),
                AllocationStrategy.ROUND_ROBIN, TODAY);
        assertEquals(id(3), out.get(0).toUserId()); // away THROUGH today inclusive; returned yesterday
    }

    @Test
    void capacityLimitsAreRespected() {
        Collector full = new Collector(id(1), "Full", null, CollectorTier.STANDARD, Set.of(), 2, null, null, null, true, 2, BigDecimal.ZERO, 0L);
        Collector smallCap = new Collector(id(2), "SmallCap", null, CollectorTier.STANDARD, Set.of(), null, d("5000"), null, null, true, 0, BigDecimal.ZERO, 0L);
        Collector portfolioCap = new Collector(id(3), "PortCap", null, CollectorTier.STANDARD, Set.of(), null, null, d("10000"), null, true, 0, d("8000"), 0L);
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "6000", CaseStatus.OPEN, 1)), List.of(full, smallCap, portfolioCap), List.of(),
                AllocationStrategy.ROUND_ROBIN, TODAY);
        assertFalse(out.get(0).allocated()); // full: 2/2 cases; smallCap: 6000 > 5000; portfolioCap: 8000+6000 > 10000
        var ok = AllocationEngine.allocateBatch(List.of(kase(2, "1500", CaseStatus.OPEN, 1)), List.of(full, smallCap, portfolioCap), List.of(),
                AllocationStrategy.ROUND_ROBIN, TODAY);
        assertTrue(ok.get(0).allocated());
        assertTrue(ok.get(0).toUserId().equals(id(2)) || ok.get(0).toUserId().equals(id(3)));
    }

    @Test
    void batchRespectsMaxOpenCasesAsItGoes() {
        Collector one = new Collector(id(1), "One", null, CollectorTier.STANDARD, Set.of(), 2, null, null, null, true, 0, BigDecimal.ZERO, 0L);
        List<CaseFacts> cases = List.of(kase(1, "100", CaseStatus.OPEN, 1), kase(2, "100", CaseStatus.OPEN, 1), kase(3, "100", CaseStatus.OPEN, 1));
        var out = AllocationEngine.allocateBatch(cases, List.of(one), List.of(), AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals(2, out.stream().filter(Decision::allocated).count());
        assertEquals(1, out.stream().filter(x -> !x.allocated()).count());
    }

    @Test
    void ruleRequiresTierAndNeverFallsBackToAnyone() {
        Collector junior = col(1, "Junior", CollectorTier.JUNIOR, null, Set.of());
        Collector senior = col(2, "Senior", CollectorTier.SENIOR, null, Set.of());
        List<RuleView> rules = List.of(rule("Large balances", 1, "50000", null, null, CollectorTier.SENIOR));
        var big = AllocationEngine.allocateBatch(List.of(kase(1, "80000", CaseStatus.OPEN, 1)), List.of(junior, senior), rules, AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals(id(2), big.get(0).toUserId());
        assertEquals("Large balances", big.get(0).ruleName());

        var onlyJunior = AllocationEngine.allocateBatch(List.of(kase(2, "80000", CaseStatus.OPEN, 1)), List.of(junior), rules, AllocationStrategy.LEAST_LOADED, TODAY);
        assertNull(onlyJunior.get(0).toUserId());
        assertTrue(onlyJunior.get(0).reason().contains("Large balances"));

        var small = AllocationEngine.allocateBatch(List.of(kase(3, "1000", CaseStatus.OPEN, 1)), List.of(junior), rules, AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals(id(1), small.get(0).toUserId()); // rule does not match a small balance
    }

    @Test
    void ruleTeamAndSkillRequirements() {
        Collector early = col(1, "Early", CollectorTier.STANDARD, "Early stage", Set.of());
        Collector legal = col(2, "Legal", CollectorTier.STANDARD, "Late stage", Set.of("legal-liaison"));
        RuleView r = new RuleView(UUID.randomUUID(), "Legal handover cases", 1, null, null, null, null, Set.of(CaseStatus.HANDED_TO_LEGAL),
                "late stage", "Legal-Liaison", null, null);
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "1000", CaseStatus.HANDED_TO_LEGAL, 1)), List.of(early, legal), List.of(r),
                AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals(id(2), out.get(0).toUserId()); // team compared case-insensitively, skill normalised
    }

    @Test
    void firstMatchingRuleByOrderWins() {
        RuleView later = rule("Second", 2, null, null, null, null);
        RuleView first = rule("First", 1, null, null, null, null);
        assertEquals("First", AllocationEngine.matchRule(List.of(later, first), kase(1, "10", CaseStatus.OPEN, 1)).name());
    }

    @Test
    void ruleConditionsOnBalanceAgeAndStatus() {
        RuleView r = new RuleView(UUID.randomUUID(), "Aged", 1, d("100"), d("1000"), 30, 90, Set.of(CaseStatus.OPEN, CaseStatus.DEMAND_SENT),
                null, null, null, null);
        assertTrue(AllocationEngine.matches(r, kase(1, "500", CaseStatus.OPEN, 60)));
        assertFalse(AllocationEngine.matches(r, kase(2, "50", CaseStatus.OPEN, 60)));      // below min balance
        assertFalse(AllocationEngine.matches(r, kase(3, "2000", CaseStatus.OPEN, 60)));    // above max balance
        assertFalse(AllocationEngine.matches(r, kase(4, "500", CaseStatus.OPEN, 10)));     // too young
        assertFalse(AllocationEngine.matches(r, kase(5, "500", CaseStatus.OPEN, 100)));    // too old
        assertFalse(AllocationEngine.matches(r, kase(6, "500", CaseStatus.DISPUTED, 60))); // wrong status
    }

    @Test
    void ruleStrategyOverridesTheDefault() {
        Collector a = col(1, "A", CollectorTier.STANDARD, null, Set.of());
        a.openCases = 5;
        a.portfolio = d("1000");
        Collector b = col(2, "B", CollectorTier.STANDARD, null, Set.of());
        b.openCases = 1;
        b.portfolio = d("90000");
        RuleView byValue = new RuleView(UUID.randomUUID(), "By value", 1, null, null, null, null, Set.of(), null, null, null, AllocationStrategy.LEAST_VALUE);
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "100", CaseStatus.OPEN, 1)), List.of(a, b), List.of(byValue), AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals(id(1), out.get(0).toUserId()); // default would pick B (fewer cases); the rule picks A (less value)
    }

    @Test
    void biggestBalancesGetFirstPick() {
        Collector senior = new Collector(id(1), "Senior", null, CollectorTier.SENIOR, Set.of(), 1, null, null, null, true, 0, BigDecimal.ZERO, 0L);
        List<RuleView> rules = List.of(rule("Large", 1, "50000", null, null, CollectorTier.SENIOR));
        var out = AllocationEngine.allocateBatch(List.of(kase(1, "200", CaseStatus.OPEN, 1), kase(2, "90000", CaseStatus.OPEN, 1)),
                List.of(senior), rules, AllocationStrategy.LEAST_LOADED, TODAY);
        assertEquals("DC-00002", out.get(0).facts().caseNumber()); // processed first
        assertEquals(id(1), out.get(0).toUserId());
        assertFalse(out.get(1).allocated()); // the small case matches no rule, but the senior now holds 1 of max 1 cases
        assertEquals(1, senior.openCases);
    }

    @Test
    void sameInputsGiveTheSamePlan() {
        List<CaseFacts> cases = List.of(kase(1, "300", CaseStatus.OPEN, 1), kase(2, "300", CaseStatus.OPEN, 1), kase(3, "300", CaseStatus.OPEN, 1));
        List<UUID> first = run(cases), second = run(cases);
        assertEquals(first, second);
    }

    private List<UUID> run(List<CaseFacts> cases) {
        List<Collector> cs = List.of(col(1, "A", CollectorTier.STANDARD, null, Set.of()), col(2, "B", CollectorTier.STANDARD, null, Set.of()));
        return AllocationEngine.allocateBatch(cases, cs, List.of(), AllocationStrategy.ROUND_ROBIN, TODAY).stream().map(Decision::toUserId).toList();
    }
}
