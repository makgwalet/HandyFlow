package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.util.*;

/** Pure rules for cost centres and the by-cost-centre profit and loss (finance F24). */
public final class CostCentreRules {
    private CostCentreRules() {}

    public static final Set<String> KINDS = Set.of("COST_CENTRE", "DEPARTMENT", "BRANCH", "PROJECT", "SITE", "VEHICLE", "PERSON");
    /** Deepest nesting allowed, so reports stay readable. */
    public static final int MAX_DEPTH = 4;

    public static String saveProblem(String code, String name, String kind) {
        if (code == null || code.isBlank()) return "Give it a code.";
        String c = code.trim();
        if (c.length() > 20) return "The code is too long (20 characters at most).";
        if (!c.matches("[A-Za-z0-9._-]+")) return "A code uses letters, digits, dots, dashes and underscores only.";
        if (name == null || name.isBlank()) return "Give it a name.";
        if (name.trim().length() > 120) return "The name is too long (120 characters at most).";
        if (kind == null || !KINDS.contains(kind)) return "Choose cost centre, department, branch, project, site, vehicle or person.";
        return null;
    }

    /** parentOf maps every centre id to its parent id (or null). Refuses a loop, an unknown parent, or nesting beyond MAX_DEPTH. */
    public static String parentProblem(UUID id, UUID newParent, Map<UUID, UUID> parentOf) {
        if (newParent == null) return null;
        if (!parentOf.containsKey(newParent)) return "The parent does not exist.";
        if (newParent.equals(id)) return "A cost centre cannot sit inside itself.";
        int depth = 1;
        UUID cur = newParent;
        Set<UUID> seen = new HashSet<>();
        while (cur != null) {
            if (cur.equals(id)) return "That would put it inside one of its own children.";
            if (!seen.add(cur)) return "The existing structure already loops. Fix the parents first.";
            cur = parentOf.get(cur);
            if (cur != null) depth++;
        }
        if (id != null && depth + 1 + height(id, parentOf) > MAX_DEPTH) return "Nesting is limited to " + MAX_DEPTH + " levels.";
        if (id == null && depth + 1 > MAX_DEPTH) return "Nesting is limited to " + MAX_DEPTH + " levels.";
        return null;
    }

    /** Number of levels below id (0 for a leaf). */
    static int height(UUID id, Map<UUID, UUID> parentOf) {
        int h = 0;
        for (UUID c : children(id, parentOf)) h = Math.max(h, 1 + height(c, parentOf));
        return h;
    }

    static List<UUID> children(UUID id, Map<UUID, UUID> parentOf) {
        List<UUID> out = new ArrayList<>();
        for (var e : parentOf.entrySet()) if (id.equals(e.getValue())) out.add(e.getKey());
        return out;
    }

    /** id plus every centre beneath it. */
    public static Set<UUID> withDescendants(UUID id, Map<UUID, UUID> parentOf) {
        Set<UUID> out = new LinkedHashSet<>();
        Deque<UUID> todo = new ArrayDeque<>();
        todo.add(id);
        while (!todo.isEmpty()) {
            UUID c = todo.poll();
            if (out.add(c)) todo.addAll(children(c, parentOf));
        }
        return out;
    }

    /** One account's result in one centre (centreId null = unallocated). Amount is already signed: income = credit - debit, expense = debit - credit. */
    public record Cell(UUID accountId, String code, String name, String type, UUID centreId, BigDecimal amount) {}
    public record PivotRow(UUID accountId, String code, String name, String type, List<BigDecimal> amounts, BigDecimal total) {}
    public record Pivot(List<PivotRow> income, List<PivotRow> expenses, List<BigDecimal> incomeTotals, List<BigDecimal> expenseTotals,
                        List<BigDecimal> resultTotals, BigDecimal incomeTotal, BigDecimal expenseTotal, BigDecimal resultTotal) {}

    /**
     * columns = centre ids in display order; an unallocated column (null) is always added last.
     * With rollUp each centre's column also includes everything beneath it; the Total column still counts every line once.
     */
    public static Pivot pivot(List<Cell> cells, List<UUID> columns, Map<UUID, UUID> parentOf, boolean rollUp) {
        List<UUID> cols = new ArrayList<>(columns);
        cols.add(null);
        Map<UUID, Integer> index = new HashMap<>();
        for (int i = 0; i < cols.size(); i++) index.put(cols.get(i), i);
        Map<UUID, Set<UUID>> below = new HashMap<>();
        if (rollUp) for (UUID c : columns) below.put(c, withDescendants(c, parentOf));

        Map<UUID, PivotRowBuilder> rows = new LinkedHashMap<>();
        for (Cell c : cells) {
            if (c.amount() == null || c.amount().signum() == 0) continue;
            PivotRowBuilder b = rows.computeIfAbsent(c.accountId(), k -> new PivotRowBuilder(c, cols.size()));
            b.own.merge(c.centreId(), c.amount(), BigDecimal::add);
            b.total = b.total.add(c.amount());
        }
        List<PivotRow> income = new ArrayList<>(), expenses = new ArrayList<>();
        for (PivotRowBuilder b : rows.values()) {
            List<BigDecimal> amounts = new ArrayList<>(Collections.nCopies(cols.size(), BigDecimal.ZERO));
            for (int i = 0; i < cols.size(); i++) {
                UUID col = cols.get(i);
                BigDecimal v = BigDecimal.ZERO;
                if (col == null || !rollUp) v = b.own.getOrDefault(col, BigDecimal.ZERO);
                else for (UUID d : below.get(col)) v = v.add(b.own.getOrDefault(d, BigDecimal.ZERO));
                amounts.set(i, v);
            }
            PivotRow r = new PivotRow(b.cell.accountId(), b.cell.code(), b.cell.name(), b.cell.type(), amounts, b.total);
            ("INCOME".equals(b.cell.type()) ? income : expenses).add(r);
        }
        Comparator<PivotRow> byCode = Comparator.comparing(PivotRow::code, Comparator.nullsLast(String::compareTo));
        income.sort(byCode);
        expenses.sort(byCode);
        List<BigDecimal> it = totals(income, cols.size()), et = totals(expenses, cols.size());
        List<BigDecimal> res = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) res.add(it.get(i).subtract(et.get(i)));
        BigDecimal itot = income.stream().map(PivotRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal etot = expenses.stream().map(PivotRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Pivot(income, expenses, it, et, res, itot, etot, itot.subtract(etot));
    }

    private static List<BigDecimal> totals(List<PivotRow> rows, int n) {
        List<BigDecimal> t = new ArrayList<>(Collections.nCopies(n, BigDecimal.ZERO));
        for (PivotRow r : rows) for (int i = 0; i < n; i++) t.set(i, t.get(i).add(r.amounts().get(i)));
        return t;
    }

    private static final class PivotRowBuilder {
        final Cell cell;
        final Map<UUID, BigDecimal> own = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        PivotRowBuilder(Cell cell, int n) { this.cell = cell; }
    }
}
