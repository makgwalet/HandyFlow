package za.co.handyflow.platform.supplychain.application.internal;

import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pure rules for allocating a parent budget to child budgets. Every method returns null when the proposal is fine, or the
 * reason it is refused, so the service can turn the text into a 409 and tests can check the wording.
 */
public final class BudgetHierarchyRules {

    private BudgetHierarchyRules() {}

    /** Deepest tree allowed: a top-level budget, its children, their children and one more. */
    public static final int MAX_DEPTH = 4;

    /** DEPARTMENT is the broadest scope, PROJECT the finest. A child may not be broader than its parent. */
    public static int rank(BudgetScopeType t) {
        return switch (t) { case DEPARTMENT -> 0; case COST_CENTRE -> 1; case PROJECT -> 2; };
    }

    public record Node(UUID id, BudgetScopeType scope, String scopeValue, LocalDate start, LocalDate end,
                       BigDecimal amount, String currency, boolean active) {}

    /** Can {@code child} be allocated out of {@code parent}? siblingsActiveTotal = active children already under the parent, excluding this child. */
    public static String attachProblem(Node parent, Node child, BigDecimal siblingsActiveTotal) {
        if (parent.id().equals(child.id())) return "A budget cannot be its own parent";
        if (!parent.active()) return "The parent budget is closed; allocate from an active budget";
        if (!child.active()) return "A closed budget cannot be allocated";
        if (!parent.currency().equalsIgnoreCase(child.currency()))
            return "The parent is in " + parent.currency() + " and the child in " + child.currency();
        if (rank(child.scope()) < rank(parent.scope()))
            return "A " + label(parent.scope()) + " budget cannot be split into a broader " + label(child.scope()) + " budget; "
                    + "children must be the same level or finer";
        if (child.start().isBefore(parent.start()) || child.end().isAfter(parent.end()))
            return "The child's period (" + child.start() + " to " + child.end() + ") must sit inside the parent's ("
                    + parent.start() + " to " + parent.end() + ")";
        return allocationProblem(parent.amount(), siblingsActiveTotal, child.amount());
    }

    /** Active children's amounts may not add up to more than the parent. */
    public static String allocationProblem(BigDecimal parentAmount, BigDecimal siblingsActiveTotal, BigDecimal childAmount) {
        BigDecimal total = siblingsActiveTotal.add(childAmount);
        if (total.compareTo(parentAmount) > 0)
            return "Allocating R " + money(childAmount) + " would take the children to R " + money(total)
                    + ", more than the parent's R " + money(parentAmount) + " (R " + money(parentAmount.subtract(siblingsActiveTotal).max(BigDecimal.ZERO))
                    + " is still unallocated)";
        return null;
    }

    /** What a parent may still spend directly: its amount less what it has handed to active children, never below zero. */
    public static BigDecimal directCap(BigDecimal parentAmount, BigDecimal allocatedToChildren) {
        return parentAmount.subtract(allocatedToChildren).max(BigDecimal.ZERO);
    }

    /** A parent cannot be reduced below what it has already handed to its active children. */
    public static String reduceProblem(BigDecimal newParentAmount, BigDecimal activeChildrenTotal) {
        if (newParentAmount.compareTo(activeChildrenTotal) < 0)
            return "R " + money(activeChildrenTotal) + " is already allocated to sub-budgets; the budget cannot go below that";
        return null;
    }

    /** Would making {@code newParent} the parent of {@code budgetId} create a loop? parentOf maps a budget id to its parent id. */
    public static boolean wouldCycle(UUID budgetId, UUID newParent, Map<UUID, UUID> parentOf) {
        UUID cursor = newParent;
        int guard = 0;
        while (cursor != null && guard++ < 1000) {
            if (cursor.equals(budgetId)) return true;
            cursor = parentOf.get(cursor);
        }
        return false;
    }

    /** Number of levels from {@code id} up to the top, counting itself (a top-level budget is depth 1). */
    public static int depthOf(UUID id, Map<UUID, UUID> parentOf) {
        int depth = 1;
        UUID cursor = parentOf.get(id);
        while (cursor != null && depth <= 1000) { depth++; cursor = parentOf.get(cursor); }
        return depth;
    }

    /** Levels at and below {@code id}, counting itself (a budget with no children is height 1). */
    public static int heightOf(UUID id, Map<UUID, List<UUID>> childrenOf) {
        int tallest = 0;
        for (UUID c : childrenOf.getOrDefault(id, List.of())) tallest = Math.max(tallest, heightOf(c, childrenOf));
        return tallest + 1;
    }

    private static String label(BudgetScopeType t) { return t.name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    private static String money(BigDecimal v) { return String.format(Locale.ROOT, "%.2f", v); }
}
