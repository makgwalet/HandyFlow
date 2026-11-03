package za.co.handyflow.platform.projects.application.internal.change;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pure rules for what a change order may do to budget and dates — no Spring, no database.
 *
 * COST: a change order's cost is described as allocations — each either adjusts an EXISTING
 * budget line (positive or negative) or creates a NEW line (positive only). The allocations must
 * add up to the declared cost impact, and no existing line may end up below what it has already
 * spent and committed. A negative impact with no allocations is rejected: a reduction has to say
 * which lines it reduces. A POSITIVE impact with no allocations is the legacy form (one new line).
 *
 * Nothing here changes any data; callers use validateAllocations() both when a change order is
 * submitted and again just before it is applied (the budget may have moved in between).
 */
public final class ChangeEffectEngine {

    private ChangeEffectEngine() {}

    public record LineState(UUID id, BigDecimal budgeted, BigDecimal actual, BigDecimal committed) {}

    /** budgetLineId == null means "create a new line in this category". */
    public record Allocation(UUID budgetLineId, String category, BigDecimal amount) {}

    public record Problem(String code, String message) {}

    public record ScheduleShift(LocalDate newEnd, LocalDate newBaselineEnd) {}

    public static List<Problem> validateAllocations(BigDecimal costImpact, List<Allocation> allocations,
                                                    Map<UUID, LineState> lines) {
        List<Problem> problems = new ArrayList<>();
        BigDecimal impact = nz(costImpact);

        if (allocations.isEmpty()) {
            if (impact.signum() < 0) {
                problems.add(new Problem("ALLOCATION_REQUIRED",
                        "A cost reduction must be allocated to the budget lines it reduces"));
            }
            return problems;
        }

        BigDecimal sum = BigDecimal.ZERO;
        Map<UUID, BigDecimal> perLine = new LinkedHashMap<>();
        for (Allocation a : allocations) {
            BigDecimal amt = nz(a.amount());
            if (amt.signum() == 0) {
                problems.add(new Problem("ZERO_AMOUNT", "A cost allocation cannot be zero"));
                continue;
            }
            sum = sum.add(amt);
            if (a.budgetLineId() == null) {
                if (a.category() == null || a.category().isBlank()) {
                    problems.add(new Problem("CATEGORY_REQUIRED", "A new budget line needs a category"));
                }
                if (amt.signum() < 0) {
                    problems.add(new Problem("NEW_LINE_NEGATIVE", "A new budget line cannot have a negative amount"));
                }
            } else if (!lines.containsKey(a.budgetLineId())) {
                problems.add(new Problem("LINE_NOT_FOUND", "Budget line " + a.budgetLineId() + " is not part of this project"));
            } else {
                perLine.merge(a.budgetLineId(), amt, BigDecimal::add);
            }
        }

        if (sum.compareTo(impact) != 0) {
            problems.add(new Problem("ALLOCATION_MISMATCH",
                    "Cost allocations total " + sum + " but the declared cost impact is " + impact));
        }

        for (Map.Entry<UUID, BigDecimal> e : perLine.entrySet()) {
            LineState l = lines.get(e.getKey());
            BigDecimal after = nz(l.budgeted()).add(e.getValue());
            BigDecimal floor = nz(l.actual()).add(nz(l.committed()));
            if (after.compareTo(floor) < 0) {
                problems.add(new Problem("BELOW_COMMITTED",
                        "Line " + l.id() + " would fall to " + after + ", below the " + floor
                                + " already spent and committed"));
            }
        }
        return problems;
    }

    /** The revised project budget once this change is applied. */
    public static BigDecimal revisedBudgetAfter(BigDecimal revisedBefore, BigDecimal costImpact,
                                                List<Allocation> allocations) {
        BigDecimal before = nz(revisedBefore);
        if (allocations.isEmpty()) {
            return nz(costImpact).signum() > 0 ? before.add(costImpact) : before;   // legacy form: one new line
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Allocation a : allocations) sum = sum.add(nz(a.amount()));
        return before.add(sum);
    }

    /**
     * Moves the project end date AND the approved baseline end by the same number of calendar days
     * (positive = delay, negative = recovery). Either may be null (nothing to move). Refuses a result
     * that would put the end date before the start.
     */
    public static ScheduleShift shiftEnd(LocalDate end, LocalDate baselineEnd, LocalDate start, int days) {
        LocalDate newEnd = end == null ? null : end.plusDays(days);
        LocalDate newBaseline = baselineEnd == null ? null : baselineEnd.plusDays(days);
        if (start != null && newEnd != null && newEnd.isBefore(start)) {
            throw new IllegalArgumentException("The schedule impact would put the end date before the start date");
        }
        return new ScheduleShift(newEnd, newBaseline);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}