package za.co.handyflow.platform.events.application.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Builds RFC 4180 CSV for the report exports.
 * <p>
 * FORMULA INJECTION: a spreadsheet treats a cell that begins with = + - or @
 * (or a tab / carriage return) as a formula. A guest who registers with the
 * name "=HYPERLINK(...)" would otherwise turn the organiser's export into an
 * attack on whoever opens it. {@link #text} therefore prefixes such cells with
 * an apostrophe, the standard defence. Side effect: a phone number written as
 * +27821234567 appears as '+27821234567 in Excel — deliberate; correctness of
 * a safe file beats a tidy phone column.
 * <p>
 * Only free text from users goes through {@link #text}. Numbers, dates and
 * timestamps are produced by the system, can't carry a payload, and are
 * written unquoted by {@link #num}, {@link #date} and {@link #time} so
 * spreadsheets sort and sum them properly.
 * <p>
 * The output starts with a UTF-8 byte-order mark so Excel opens accented
 * names (Afrikaans, isiZulu, etc.) correctly instead of mangling them.
 */
final class CsvWriter {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringBuilder out = new StringBuilder("\uFEFF");

    /** Appends one row of already-formatted cells (use text/num/date/time/bool to format them). */
    CsvWriter row(String... cells) {
        out.append(String.join(",", cells)).append("\r\n");
        return this;
    }

    byte[] toBytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Cell formatters ───────────────────────────────────────────────────────

    /** User-supplied text: injection-guarded, always quoted. null becomes an empty cell. */
    static String text(String value) {
        if (value == null) return "";
        String s = value;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    static String num(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    static String num(long value) {
        return Long.toString(value);
    }

    static String bool(boolean value) {
        return value ? "true" : "false";
    }

    static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    /** Timestamps are shown in South African time, the way the organiser thinks about the event. */
    static String time(Instant value) {
        return value == null ? "" : TIME_FMT.format(value.atZone(SAST));
    }
}