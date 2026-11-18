package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.BudgetHierarchyRules;
import za.co.handyflow.platform.supplychain.application.internal.BudgetHierarchyRules.Node;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetHierarchyRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static final LocalDate FY_START = LocalDate.of(2026, 3, 1), FY_END = LocalDate.of(2027, 2, 28);

    private static Node node(BudgetScopeType t, String amount, LocalDate s, LocalDate e, String ccy, boolean active) {
        return new Node(UUID.randomUUID(), t, "X", s, e, bd(amount), ccy, active);
    }
    private static Node dept(String amount) { return node(BudgetScopeType.DEPARTMENT, amount, FY_START, FY_END, "ZAR", true); }
    private static Node project(String amount) { return node(BudgetScopeType.PROJECT, amount, FY_START, FY_END, "ZAR", true); }

    @Test
    void aFinerChildInsideThePeriodAndAmountIsAccepted() {
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000000"), project("400000"), bd("500000"))).isNull();
    }

    @Test
    void childrenMayUseTheWholeParentButNotMore() {
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000000"), project("500000"), bd("500000"))).isNull();
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000000"), project("500000.01"), bd("500000")))
                .contains("more than the parent's R 1000000.00").contains("R 500000.00 is still unallocated");
    }

    @Test
    void aChildBroaderThanItsParentIsRefused() {
        assertThat(BudgetHierarchyRules.attachProblem(project("1000"), dept("100"), BigDecimal.ZERO))
                .contains("same level or finer");
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000"), dept("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void theChildPeriodMustSitInsideTheParents() {
        Node late = node(BudgetScopeType.PROJECT, "10", FY_START, FY_END.plusDays(1), "ZAR", true);
        Node early = node(BudgetScopeType.PROJECT, "10", FY_START.minusDays(1), FY_END, "ZAR", true);
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000"), late, BigDecimal.ZERO)).contains("must sit inside");
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000"), early, BigDecimal.ZERO)).contains("must sit inside");
    }

    @Test
    void closedBudgetsAndMixedCurrenciesAreRefused() {
        assertThat(BudgetHierarchyRules.attachProblem(node(BudgetScopeType.DEPARTMENT, "1000", FY_START, FY_END, "ZAR", false),
                project("10"), BigDecimal.ZERO)).contains("closed");
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000"),
                node(BudgetScopeType.PROJECT, "10", FY_START, FY_END, "ZAR", false), BigDecimal.ZERO)).contains("closed");
        assertThat(BudgetHierarchyRules.attachProblem(dept("1000"),
                node(BudgetScopeType.PROJECT, "10", FY_START, FY_END, "USD", true), BigDecimal.ZERO)).contains("USD");
    }

    @Test
    void aBudgetCannotBeItsOwnParent() {
        Node n = dept("100");
        assertThat(BudgetHierarchyRules.attachProblem(n, n, BigDecimal.ZERO)).contains("own parent");
    }

    @Test
    void aParentCannotBeReducedBelowWhatItHasAllocated() {
        assertThat(BudgetHierarchyRules.reduceProblem(bd("900"), bd("900"))).isNull();
        assertThat(BudgetHierarchyRules.reduceProblem(bd("899.99"), bd("900"))).contains("already allocated");
        assertThat(BudgetHierarchyRules.reduceProblem(bd("1"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void loopsAreDetectedAtAnyDepth() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, UUID> parentOf = new HashMap<>();
        parentOf.put(b, a);   // b is under a
        parentOf.put(c, b);   // c is under b
        assertThat(BudgetHierarchyRules.wouldCycle(a, c, parentOf)).isTrue();   // a under c would loop
        assertThat(BudgetHierarchyRules.wouldCycle(a, b, parentOf)).isTrue();
        assertThat(BudgetHierarchyRules.wouldCycle(c, a, parentOf)).isFalse();  // c already below a; moving it to a is fine
        assertThat(BudgetHierarchyRules.wouldCycle(a, a, parentOf)).isTrue();
        assertThat(BudgetHierarchyRules.wouldCycle(a, null, parentOf)).isFalse();
    }

    @Test
    void depthAndHeightCountLevels() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, UUID> parentOf = Map.of(b, a, c, b);
        Map<UUID, List<UUID>> childrenOf = Map.of(a, List.of(b), b, List.of(c));
        assertThat(BudgetHierarchyRules.depthOf(a, parentOf)).isEqualTo(1);
        assertThat(BudgetHierarchyRules.depthOf(c, parentOf)).isEqualTo(3);
        assertThat(BudgetHierarchyRules.heightOf(a, childrenOf)).isEqualTo(3);
        assertThat(BudgetHierarchyRules.heightOf(c, childrenOf)).isEqualTo(1);
    }

    @Test
    void directSpendMayOnlyUseWhatIsNotAllocatedToChildren() {
        assertThat(BudgetHierarchyRules.directCap(new java.math.BigDecimal("1000000"), new java.math.BigDecimal("600000"))).isEqualByComparingTo("400000");
        assertThat(BudgetHierarchyRules.directCap(new java.math.BigDecimal("500"), new java.math.BigDecimal("500"))).isEqualByComparingTo("0");
        assertThat(BudgetHierarchyRules.directCap(new java.math.BigDecimal("500"), new java.math.BigDecimal("700"))).isEqualByComparingTo("0");
    }
}
