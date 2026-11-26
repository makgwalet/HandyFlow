package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Plain CSV writing for the files the bureau hands out. */
public final class CsvRules {

    private CsvRules() {}

    /**
     * A cell safe to open in a spreadsheet. Quotes and commas are escaped; a text cell that begins with =, +, - or @ (or a tab or
     * carriage return) is prefixed with an apostrophe so a spreadsheet does not run it as a formula. Numbers are written with {@link #money}.
     */
    public static String cell(String value) {
        if (value == null) return "";
        String v = value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty()) {
            char c = v.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t') v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"")) v = "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    /** Two decimals with a dot, never scientific notation: the same on every machine. */
    public static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String row(List<String> cells) {
        return String.join(",", cells) + "\r\n";
    }
}
