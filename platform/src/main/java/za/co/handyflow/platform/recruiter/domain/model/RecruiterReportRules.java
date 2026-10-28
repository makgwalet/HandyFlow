package za.co.handyflow.platform.recruiter.domain.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * What every recruiting report shares: the date range, how a rate is worked out and how a table becomes a CSV. Pure. Keeping these here means the
 * same figure is calculated the same way on every screen and in every export.
 */
public final class RecruiterReportRules {
    private RecruiterReportRules() {}

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    public static final int DEFAULT_DAYS = 90, MAX_DAYS = 731;

    /** Why this date range cannot be used, or null. Both dates are optional: the default is the last 90 days. */
    public static String rangeProblem(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate f = fromOrDefault(from, to, today), t = toOrDefault(to, today);
        if (f.isAfter(t)) return "The start date is after the end date.";
        if (t.isAfter(today)) return "The end date cannot be in the future.";
        if (f.plusDays(MAX_DAYS).isBefore(t)) return "Choose a range of two years or less.";
        return null;
    }

    public static LocalDate toOrDefault(LocalDate to, LocalDate today) { return to != null ? to : today; }

    public static LocalDate fromOrDefault(LocalDate from, LocalDate to, LocalDate today) {
        return from != null ? from : toOrDefault(to, today).minusDays(DEFAULT_DAYS - 1);
    }

    /** First instant of the start date, South African time. */
    public static java.time.Instant start(LocalDate from) { return from.atStartOfDay(ZONE).toInstant(); }

    /** First instant after the end date: the range includes the whole of its last day. */
    public static java.time.Instant endExclusive(LocalDate to) { return to.plusDays(1).atStartOfDay(ZONE).toInstant(); }

    /** Percentage with one decimal, or null when there is nothing to divide by (shown as a dash, never as 0%). */
    public static Double percent(long part, long whole) {
        if (whole <= 0) return null;
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    /** Median of the values, or null when there are none. */
    public static Double median(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        List<Double> s = values.stream().sorted().toList();
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    /**
     * One CSV cell. Quoted when needed, and text that a spreadsheet would run as a formula (starting with = + - @) is made harmless, since report
     * rows include text candidates typed.
     */
    public static String csvCell(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(value instanceof Number)) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    public static String csv(List<String> columns, List<List<Object>> rows) {
        StringBuilder b = new StringBuilder();
        b.append(String.join(",", columns.stream().map(RecruiterReportRules::csvCell).toList())).append("\r\n");
        for (List<Object> r : rows) b.append(String.join(",", r.stream().map(RecruiterReportRules::csvCell).toList())).append("\r\n");
        return b.toString();
    }
}
