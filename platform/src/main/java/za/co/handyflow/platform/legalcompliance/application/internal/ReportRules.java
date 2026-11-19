package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.LocalDate;
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

    public static String fileName(String key, LocalDate on) { return "legal-compliance-" + key.toLowerCase().replace('_', '-') + "-" + on + ".csv"; }
}
