package za.co.handyflow.platform.projects.application.internal.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure cost-control arithmetic — no Spring, no database — so every rule is unit-testable.
 *
 * VOCABULARY (the roadmap's "never mix these up"):
 *   budgeted   — the CURRENT approved budget of a line (original ± approved revisions/transfers)
 *   original   — the budget approved at baseline (null = line added later, i.e. a variation)
 *   committed  — promised, not yet incurred (open purchase orders)
 *   actual     — incurred
 *   etc        — estimate to complete: ADDITIONAL cost still expected beyond actual + committed.
 *                Manual when a cost controller sets it; otherwise the budget still unspent
 *                (max(0, budgeted − actual − committed)), i.e. "no overrun predicted unless
 *                money already spent/committed exceeds the budget".
 *   eac        — estimate at completion = actual + committed + etc
 *   vac        — variance at completion = budgeted − eac   (negative = forecast overrun)
 *   proposed   — SUBMITTED change orders. Reported as exposure ONLY; they never change budgeted.
 *
 * Category quirk: ChangeOrderService approves a change order by adding a line whose category is
 * CONTINGENCY and whose description starts with "Change Order:". Those are approved VARIATIONS,
 * not management contingency, so they are reported under "VARIATIONS".
 */
public final class CostEngine {

    private CostEngine() {}

    public static final String VARIATIONS    = "VARIATIONS";
    public static final String CONTINGENCY   = "CONTINGENCY";
    private static final String CO_PREFIX    = "Change Order:";
    private static final BigDecimal HUNDRED  = BigDecimal.valueOf(100);

    public record Line(UUID id, String category, String description,
                       BigDecimal budgeted, BigDecimal original,
                       BigDecimal committed, BigDecimal actual, BigDecimal etc) {}

    public record LineResult(UUID id, String category, BigDecimal budgeted, BigDecimal original,
                             BigDecimal committed, BigDecimal actual, BigDecimal remaining,
                             BigDecimal etc, boolean etcManual, BigDecimal eac, BigDecimal vac,
                             BigDecimal consumedPct, boolean overBudget) {}

    public record CategoryTotals(BigDecimal budgeted, BigDecimal committed, BigDecimal actual, BigDecimal eac) {}

    /**
     * baselineCaptured=false → originalBudget / additions / netAdjustments are null (nothing has
     * been approved yet, so there is nothing to compare against).
     */
    public record Summary(
            boolean baselineCaptured,
            BigDecimal originalBudget, BigDecimal revisedBudget,
            BigDecimal additions, BigDecimal netAdjustments,
            BigDecimal committed, BigDecimal actual, BigDecimal remaining,
            BigDecimal etc, BigDecimal eac, BigDecimal vac, BigDecimal consumedPct,
            int pendingChangeOrderCount, BigDecimal pendingChangeOrderValue, BigDecimal eacIncludingPending,
            BigDecimal contingencyBudget, BigDecimal contingencyUsed, BigDecimal contingencyRemaining,
            BigDecimal contractValue, BigDecimal forecastMargin, BigDecimal forecastMarginPct,
            int overBudgetLineCount,
            List<LineResult> lines, Map<String, CategoryTotals> categories) {}

    public static Summary summarize(List<Line> lines, int pendingCount, BigDecimal pendingValue,
                                    BigDecimal contractValue) {
        boolean captured = lines.stream().anyMatch(l -> l.original() != null);

        BigDecimal original = BigDecimal.ZERO, revised = BigDecimal.ZERO;
        BigDecimal additions = BigDecimal.ZERO, adjustments = BigDecimal.ZERO;
        BigDecimal committed = BigDecimal.ZERO, actual = BigDecimal.ZERO;
        BigDecimal etcTotal = BigDecimal.ZERO, eacTotal = BigDecimal.ZERO;
        BigDecimal contBudget = BigDecimal.ZERO, contUsed = BigDecimal.ZERO;
        int overCount = 0;

        List<LineResult> results = new ArrayList<>();
        Map<String, BigDecimal[]> cats = new TreeMap<>();   // {budgeted, committed, actual, eac}

        for (Line l : lines) {
            BigDecimal budgeted = nz(l.budgeted()), com = nz(l.committed()), act = nz(l.actual());
            boolean manual = l.etc() != null;
            BigDecimal etc = manual ? l.etc() : maxTransferable(budgeted, act, com);
            BigDecimal eac = act.add(com).add(etc);
            BigDecimal spent = act.add(com);
            boolean over = spent.compareTo(budgeted) > 0;
            if (over) overCount++;

            results.add(new LineResult(l.id(), categoryOf(l), budgeted, l.original(), com, act,
                    budgeted.subtract(spent), etc, manual, eac, budgeted.subtract(eac),
                    pct(spent, budgeted), over));

            revised = revised.add(budgeted);
            committed = committed.add(com);
            actual = actual.add(act);
            etcTotal = etcTotal.add(etc);
            eacTotal = eacTotal.add(eac);
            if (l.original() != null) {
                original = original.add(l.original());
                adjustments = adjustments.add(budgeted.subtract(l.original()));
            } else {
                additions = additions.add(budgeted);
            }

            String cat = categoryOf(l);
            if (CONTINGENCY.equals(cat)) { contBudget = contBudget.add(budgeted); contUsed = contUsed.add(spent); }
            BigDecimal[] t = cats.computeIfAbsent(cat, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(budgeted); t[1] = t[1].add(com); t[2] = t[2].add(act); t[3] = t[3].add(eac);
        }

        Map<String, CategoryTotals> categories = new LinkedHashMap<>();
        cats.forEach((k, t) -> categories.put(k, new CategoryTotals(t[0], t[1], t[2], t[3])));

        BigDecimal pending = nz(pendingValue);
        BigDecimal margin = null, marginPct = null;
        if (contractValue != null && contractValue.signum() > 0) {
            margin = contractValue.subtract(eacTotal);
            marginPct = margin.multiply(HUNDRED).divide(contractValue, 2, RoundingMode.HALF_UP);
        }

        return new Summary(captured,
                captured ? original : null, revised,
                captured ? additions : null, captured ? adjustments : null,
                committed, actual, revised.subtract(actual).subtract(committed),
                etcTotal, eacTotal, revised.subtract(eacTotal), pct(actual.add(committed), revised),
                pendingCount, pending, eacTotal.add(pending),
                contBudget, contUsed, contBudget.subtract(contUsed),
                contractValue, margin, marginPct, overCount, results, categories);
    }

    /** Budget on a line that is neither spent nor committed — also the most that can be moved off it. */
    public static BigDecimal maxTransferable(BigDecimal budgeted, BigDecimal actual, BigDecimal committed) {
        BigDecimal free = nz(budgeted).subtract(nz(actual)).subtract(nz(committed));
        return free.signum() > 0 ? free : BigDecimal.ZERO;
    }

    static String categoryOf(Line l) {
        if (l.description() != null && l.description().startsWith(CO_PREFIX)) return VARIATIONS;
        return (l.category() == null || l.category().isBlank()) ? "UNCATEGORISED" : l.category();
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole.signum() > 0
                ? part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}