package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/** Pure helpers for building report cells and CSV files. No framework dependencies. */
public final class ReportRules {

    private ReportRules() {}

    /** Reports stop at this many rows; the result says when it was cut. */
    public static final int MAX_ROWS = 5000;

    private static final Pattern NUMBER = Pattern.compile("^-?\\d+([.,]\\d+)?$");

    public static String text(Object o) { return o == null ? "" : String.valueOf(o).replaceAll("[\\r\\n]+", " ").trim(); }

    public static String date(LocalDate d) { return d == null ? "" : d.toString(); }

    /** The South African calendar date of an instant, or empty. */
    public static String dateOf(Instant t) { return t == null ? "" : t.atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate().toString(); }

    public static String yesNo(boolean b) { return b ? "Yes" : "No"; }

    public static String yesNo(Boolean b) { return b == null ? "" : yesNo(b.booleanValue()); }

    public static String num(Number n) { return n == null ? "" : String.valueOf(n); }

    /** Whole days past the due date, 0 when not past it or when there is no due date. */
    public static long daysOverdue(LocalDate due, LocalDate today) { return due == null || !today.isAfter(due) ? 0 : ChronoUnit.DAYS.between(due, today); }

    /**
     * Guard against spreadsheet formula injection: a cell that a spreadsheet would read as a formula (starts with = + - @, tab or carriage return) gets a leading
     * apostrophe, except plain numbers such as -3. Report cells contain user-entered text, so this matters.
     */
    public static String safeCell(String s) {
        if (s == null || s.isEmpty()) return "";
        char c = s.charAt(0);
        if ((c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') && !NUMBER.matcher(s).matches()) return "'" + s;
        return s;
    }

    /** RFC 4180 quoting: quote when a cell has a comma, quote or line break; double any quote. */
    public static String csvCell(String s) {
        String v = safeCell(s);
        return v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** CSV with CRLF line ends, ready to be written with a UTF-8 byte-order mark so Excel reads accented names correctly. */
    public static String csv(List<String> columns, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", columns.stream().map(ReportRules::csvCell).toList())).append("\r\n");
        for (List<String> r : rows) sb.append(String.join(",", r.stream().map(ReportRules::csvCell).toList())).append("\r\n");
        return sb.toString();
    }

    public static String fileName(String key, LocalDate on) { return fileName(key, on, "csv"); }

    public static String fileName(String key, LocalDate on, String ext) { return "legal-compliance-" + key.toLowerCase().replace('_', '-') + "-" + on + "." + ext; }

    /** The line under the title in XLSX and PDF output: who ran it, when, as at which date, and whether it was cut. */
    public static String subtitle(java.time.Instant at, String by, String asOf, int rows, boolean truncated) {
        String when = at == null ? "" : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.of("Africa/Johannesburg")).format(at);
        return "Generated " + when + (by == null || by.isBlank() ? "" : " by " + by) + (asOf == null ? "" : " · as of " + asOf) + " · " + rows + " row(s)"
                + (truncated ? " · CUT at " + MAX_ROWS + " rows: narrow the report to see the rest" : "");
    }

    // ── Board packs ──

    public static final int MAX_PACK_REPORTS = 20, PACK_ROWS_PER_REPORT = 1000;

    /** The report keys of a pack: at least one, no more than the cap, upper-cased, duplicates dropped, order kept. */
    public static java.util.List<String> packKeys(java.util.List<String> keys) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        if (keys != null) for (String k : keys) if (k != null && !k.isBlank()) set.add(k.trim().toUpperCase(java.util.Locale.ROOT));
        if (set.isEmpty()) throw new IllegalArgumentException("Choose at least one report");
        if (set.size() > MAX_PACK_REPORTS) throw new IllegalArgumentException("A pack can hold at most " + MAX_PACK_REPORTS + " reports");
        return java.util.List.copyOf(set);
    }

    public static String packTitle(String title) {
        String t = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return "Legal & Compliance board pack";
        return t.length() > 100 ? t.substring(0, 100).trim() : t;
    }

    public static String packSubtitle(LocalDate on, String by, int reports, String asOf) {
        return "Prepared " + on + (by == null || by.isBlank() ? "" : " by " + by) + " · " + reports + " report(s)" + (asOf == null || asOf.isBlank() ? "" : " · scorecard as of " + asOf.trim());
    }

    /** True when a report has more rows than a pack carries. The pack says so under that report's title. */
    public static boolean packCuts(int rows) { return rows > PACK_ROWS_PER_REPORT; }

    public static String packNote(int rows) {
        return packCuts(rows) ? " · the pack shows the first " + PACK_ROWS_PER_REPORT + " of " + rows + " rows: export the report for all of them" : "";
    }

    public static String packFileName(LocalDate on) { return "legal-compliance-board-pack-" + on + ".pdf"; }
}
