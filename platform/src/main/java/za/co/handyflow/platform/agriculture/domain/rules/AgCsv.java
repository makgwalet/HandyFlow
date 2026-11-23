package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CSV for the registers. Quotes and line breaks are escaped (RFC 4180), and text that a spreadsheet would run as a formula
 * ("=cmd...", "+1", "-1", "@SUM") gets a leading apostrophe, so a hostile tag or note cannot execute when an auditor opens the file.
 * Numbers and dates are written by {@link #num} and {@link #day}, never through {@link #text}, so they stay numbers.
 */
public final class AgCsv {

    public static final String BOM = "﻿";   // so Excel reads the file as UTF-8
    public static final String EOL = "\r\n";

    private AgCsv() {}

    /** Free text: trimmed, formula-guarded, quoted when needed. Null is an empty cell. */
    public static String text(String value) {
        if (value == null) return "";
        String v = value.strip();
        if (v.isEmpty()) return "";
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') v = "'" + v;
        return quote(v);
    }

    public static String num(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }
    public static String num(Number value) { return value == null ? "" : value.toString(); }
    public static String day(LocalDate value) { return value == null ? "" : value.toString(); }

    private static String quote(String v) {
        boolean needs = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return needs ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** One line from cells already made with text/num/day. */
    public static String line(List<String> cells) { return String.join(",", cells) + EOL; }

    /** A download name made only of letters, digits, dot, dash and underscore: safe in a header and on any file system. */
    public static String fileName(String prefix, String name, LocalDate day, String extension) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40);
        return prefix + (cleaned.isEmpty() ? "" : "-" + cleaned) + "-" + day + "." + extension;
    }
}
