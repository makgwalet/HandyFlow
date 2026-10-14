package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure rules for the profitability view by cost centre (finance F28). Income and expenses come in by cost centre and
 * account subtype. The view keeps the expense split by subtype so the person can decide which subtypes count as direct costs.
 */
public final class ProfitabilityRules {
    private ProfitabilityRules() {}

    /** centreId null = unallocated. Income is positive for income; expense is positive for cost. */
    public record Cell(UUID centreId, String type, String subtype, BigDecimal amount) {
        String subtypeOrOther() { return subtype == null || subtype.isBlank() ? NO_SUBTYPE : subtype; }
    }
    public record Row(UUID centreId, BigDecimal income, Map<String, BigDecimal> expenses) {
        public BigDecimal expenseTotal() { return expenses.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
        public BigDecimal profit() { return income.subtract(expenseTotal()); }
    }
    public record View(List<Row> rows, Row unallocated, Row total, List<String> subtypes) {}

    public static final String NO_SUBTYPE = "OTHER";

    private static final class Acc {
        final UUID id; BigDecimal income = BigDecimal.ZERO; final Map<String, BigDecimal> expenses = new TreeMap<>();
        Acc(UUID id) { this.id = id; }
        void add(String type, String sub, BigDecimal amount) {
            if ("INCOME".equals(type)) income = income.add(amount); else expenses.merge(sub, amount, BigDecimal::add);
        }
        void merge(Acc o) { income = income.add(o.income); o.expenses.forEach((k, v) -> expenses.merge(k, v, BigDecimal::add)); }
        Row row() { return new Row(id, income, new TreeMap<>(expenses)); }
    }

    /**
     * shown = centres to list. With rollUp each listed centre also includes everything beneath it (so a branch includes its sites);
     * the total always counts every line once.
     */
    public static View build(List<Cell> cells, List<UUID> shown, Map<UUID, UUID> parentOf, boolean rollUp) {
        Map<UUID, Acc> own = new HashMap<>();
        Acc unalloc = new Acc(null), total = new Acc(null);
        TreeSet<String> subtypes = new TreeSet<>();
        for (Cell c : cells) {
            if (c.amount() == null || c.amount().signum() == 0) continue;
            String sub = c.subtypeOrOther();
            (c.centreId() == null ? unalloc : own.computeIfAbsent(c.centreId(), Acc::new)).add(c.type(), sub, c.amount());
            total.add(c.type(), sub, c.amount());
            if ("EXPENSE".equals(c.type())) subtypes.add(sub);
        }
        List<Row> rows = new ArrayList<>();
        for (UUID id : shown) {
            Acc r = new Acc(id);
            Collection<UUID> ids = rollUp ? CostCentreRules.withDescendants(id, parentOf) : List.of(id);
            for (UUID d : ids) { Acc o = own.get(d); if (o != null) r.merge(o); }
            rows.add(r.row());
        }
        return new View(rows, unalloc.row(), total.row(), new ArrayList<>(subtypes));
    }

    /** Margin as a percentage of income, one decimal, or null when there is no income. */
    public static BigDecimal marginPercent(BigDecimal income, BigDecimal profit) {
        if (income == null || income.signum() <= 0) return null;
        return profit.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);
    }

    /** Expenses whose subtype is in the chosen set count as direct; everything else is overhead. */
    public static BigDecimal direct(Row r, Set<String> directSubtypes) {
        return r.expenses().entrySet().stream().filter(e -> directSubtypes.contains(e.getKey())).map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
