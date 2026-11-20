package za.co.handyflow.platform.legalcompliance.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pure rules for a custom report: a view over a standard report's own table. It can only choose among the standard report's columns, keep rows by simple tests on them, and
 * sort. It cannot add a column, join anything or reach data the standard report does not already show. Comparing (greater than, less than, sorting) treats two values as
 * numbers if both are numbers, as dates if both are ISO dates (2026-03-31), and otherwise as text ignoring case. A value that does not parse never matches a greater/less test.
 */
public final class CustomReportRules {

    private CustomReportRules() {}

    public static final int MAX_COLUMNS = 30, MAX_FILTERS = 10, MAX_VALUE = 200, MAX_NAME = 120, MAX_DESCRIPTION = 500, MAX_PER_USER = 50;

    public enum Op { EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, STARTS_WITH, GT, GTE, LT, LTE, EMPTY, NOT_EMPTY;
        public boolean needsValue() { return this != EMPTY && this != NOT_EMPTY; } }

    public record Filter(String column, Op op, String value) {}

    public record Def(String baseKey, List<String> columns, List<Filter> filters, String sortColumn, boolean sortDesc) {}

    public record Result(List<String> columns, List<List<String>> rows) {}

    /** Why the definition is not valid for a report with these columns, or null. */
    public static String problem(Def d, List<String> baseColumns) {
        if (d == null || d.baseKey() == null || d.baseKey().isBlank()) return "Choose the standard report this is built on";
        if (d.columns() == null || d.columns().isEmpty()) return "Choose at least one column";
        if (d.columns().size() > MAX_COLUMNS) return "At most " + MAX_COLUMNS + " columns";
        if (new HashSet<>(d.columns()).size() != d.columns().size()) return "A column is chosen twice";
        for (String c : d.columns()) if (!baseColumns.contains(c)) return "The report has no column \"" + c + "\"";
        List<Filter> fs = d.filters() == null ? List.of() : d.filters();
        if (fs.size() > MAX_FILTERS) return "At most " + MAX_FILTERS + " filters";
        for (Filter f : fs) {
            if (f == null || f.op() == null) return "Every filter needs a test";
            if (f.column() == null || !baseColumns.contains(f.column())) return "A filter uses a column the report does not have: \"" + (f == null ? "" : f.column()) + "\"";
            if (f.op().needsValue() && (f.value() == null || f.value().isBlank())) return "The filter on \"" + f.column() + "\" needs a value";
            if (f.value() != null && f.value().length() > MAX_VALUE) return "A filter value is too long (" + MAX_VALUE + " characters at most)";
        }
        if (d.sortColumn() != null && !d.sortColumn().isBlank() && !baseColumns.contains(d.sortColumn())) return "Cannot sort by \"" + d.sortColumn() + "\": the report has no such column";
        return null;
    }

    public static String nameProblem(String name, String description) {
        if (name == null || name.trim().length() < 3) return "Give the report a name of at least 3 characters";
        if (name.trim().length() > MAX_NAME) return "The name is too long (" + MAX_NAME + " characters at most)";
        if (description != null && description.length() > MAX_DESCRIPTION) return "The description is too long (" + MAX_DESCRIPTION + " characters at most)";
        return null;
    }

    /** Applies the definition to the standard report's columns and rows. Filters first (on all of the standard report's columns), then the sort, then the chosen columns. */
    public static Result apply(Def d, List<String> baseColumns, List<List<String>> rows) {
        List<Filter> fs = d.filters() == null ? List.of() : d.filters();
        List<List<String>> kept = new ArrayList<>();
        for (List<String> row : rows) {
            boolean ok = true;
            for (Filter f : fs) if (!matches(f, cell(row, baseColumns.indexOf(f.column())))) { ok = false; break; }
            if (ok) kept.add(row);
        }
        if (d.sortColumn() != null && !d.sortColumn().isBlank()) {
            int s = baseColumns.indexOf(d.sortColumn());
            Comparator<List<String>> byValue = (a, b) -> compare(cell(a, s), cell(b, s));
            Comparator<List<String>> cmp = (a, b) -> {
                boolean ea = cell(a, s).isBlank(), eb = cell(b, s).isBlank();
                if (ea || eb) return ea == eb ? 0 : ea ? 1 : -1; // blanks always last
                return d.sortDesc() ? byValue.reversed().compare(a, b) : byValue.compare(a, b);
            };
            kept.sort(cmp); // List.sort is stable
        }
        int[] idx = d.columns().stream().mapToInt(baseColumns::indexOf).toArray();
        List<List<String>> out = new ArrayList<>(kept.size());
        for (List<String> row : kept) {
            List<String> r = new ArrayList<>(idx.length);
            for (int i : idx) r.add(cell(row, i));
            out.add(r);
        }
        return new Result(List.copyOf(d.columns()), out);
    }

    static String cell(List<String> row, int i) { return i < 0 || i >= row.size() || row.get(i) == null ? "" : row.get(i); }

    static boolean matches(Filter f, String cell) {
        String v = f.value() == null ? "" : f.value();
        String a = cell.trim(), b = v.trim();
        switch (f.op()) {
            case EMPTY: return a.isEmpty();
            case NOT_EMPTY: return !a.isEmpty();
            case EQUALS: return eq(a, b);
            case NOT_EQUALS: return !eq(a, b);
            case CONTAINS: return a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case NOT_CONTAINS: return !a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case STARTS_WITH: return a.toLowerCase(Locale.ROOT).startsWith(b.toLowerCase(Locale.ROOT));
            default: {
                Integer c = orderedCompare(a, b);
                if (c == null) return false;
                return switch (f.op()) { case GT -> c > 0; case GTE -> c >= 0; case LT -> c < 0; default -> c <= 0; };
            }
        }
    }

    private static boolean eq(String a, String b) {
        BigDecimal x = num(a), y = num(b);
        if (x != null && y != null) return x.compareTo(y) == 0;
        return a.equalsIgnoreCase(b);
    }

    /** Numbers, then dates; null when the two do not parse as the same kind. */
    private static Integer orderedCompare(String a, String b) {
        BigDecimal x = num(a), y = num(b);
        if (x != null && y != null) return x.compareTo(y);
        LocalDate p = date(a), q = date(b);
        if (p != null && q != null) return p.compareTo(q);
        return null;
    }

    static int compare(String a, String b) {
        Integer c = orderedCompare(a.trim(), b.trim());
        return c != null ? c : a.compareToIgnoreCase(b);
    }

    private static BigDecimal num(String s) {
        String t = s.replace(" ", "").replace(" ", "");
        if (t.isEmpty() || !t.matches("[-+]?\\d+(\\.\\d+)?")) return null;
        return new BigDecimal(t);
    }

    private static LocalDate date(String s) {
        if (s.length() != 10) return null;
        try { return LocalDate.parse(s); } catch (RuntimeException e) { return null; }
    }
}
