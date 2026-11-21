package za.co.handyflow.platform.security.application.internal;

import java.math.BigDecimal;

/**
 * Cell formatting for the payroll CSV. Money and hours never go through the JVM's default locale (en-ZA writes a decimal comma, which splits a column), and text is
 * quoted and defused: a guard name that starts with = + - or @ would otherwise run as a formula when the file is opened in a spreadsheet.
 */
public final class PayrollCsv {

    private PayrollCsv() {}

    /** Always quoted; quotes doubled; a leading formula character gets an apostrophe in front so it stays text. */
    public static String text(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty()) {
            char c = v.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t') v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** Rand and cents with a point, e.g. 123456 -> 1234.56; negative values keep their sign. */
    public static String money(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    public static String hours(BigDecimal hours) {
        return (hours == null ? BigDecimal.ZERO : hours).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
