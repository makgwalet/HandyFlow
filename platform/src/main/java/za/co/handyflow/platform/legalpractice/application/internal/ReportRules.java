package za.co.handyflow.platform.legalpractice.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Plain rules behind the firm reports: ageing, money maths and CSV. No database, no Spring. */
public final class ReportRules {

    private ReportRules() { }

    public static final int ROW_LIMIT = 2000;
    public static final long MAX_RANGE_DAYS = 3660;
    /** Keys and labels of the ageing buckets, oldest last. Current means not yet due (or no due date). */
    public static final List<String[]> BUCKETS = List.of(new String[]{"CURRENT", "Not yet due"}, new String[]{"D1_30", "1 to 30 days overdue"}, new String[]{"D31_60", "31 to 60 days overdue"},
            new String[]{"D61_90", "61 to 90 days overdue"}, new String[]{"D90_PLUS", "Over 90 days overdue"});

    public static BigDecimal money(BigDecimal v) { return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP); }

    public static BigDecimal outstanding(BigDecimal total, BigDecimal paid) {
        BigDecimal o = money(total).subtract(money(paid));
        return o.signum() < 0 ? BigDecimal.ZERO.setScale(2) : o;
    }

    public static long daysOverdue(LocalDate due, LocalDate today) {
        return due == null || !due.isBefore(today) ? 0 : ChronoUnit.DAYS.between(due, today);
    }

    public static String bucket(long daysOverdue) {
        return daysOverdue <= 0 ? "CURRENT" : daysOverdue <= 30 ? "D1_30" : daysOverdue <= 60 ? "D31_60" : daysOverdue <= 90 ? "D61_90" : "D90_PLUS";
    }

    /** Percentage of what was invoiced that has been paid, one decimal place. Nothing invoiced is 0, not a divide by zero. */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) return BigDecimal.ZERO.setScale(1);
        return money(part).multiply(BigDecimal.valueOf(100)).divide(money(whole), 1, RoundingMode.HALF_UP);
    }

    public static String rangeProblem(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "Choose a start date and an end date";
        if (to.isBefore(from)) return "The end date cannot be before the start date";
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) return "Choose a range of up to ten years";
        return null;
    }

    /**
     * One CSV cell. Text that a spreadsheet could read as a formula (starts with = + - @ tab or return) gets a leading apostrophe, so a client or matter name
     * cannot run anything when the file is opened. Numbers and dates are written as they are, so negative amounts stay numbers.
     */
    public static String cell(Object v) {
        if (v == null) return "";
        String s = v instanceof BigDecimal b ? b.toPlainString() : v.toString();
        if (v instanceof CharSequence && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    public static String csv(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", header.stream().map(ReportRules::cell).toList())).append("\r\n");
        for (List<Object> r : rows) sb.append(String.join(",", r.stream().map(ReportRules::cell).toList())).append("\r\n");
        return sb.toString();
    }
}
