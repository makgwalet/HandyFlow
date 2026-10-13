package za.co.handyflow.platform.debtcollection.application.internal;

import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 4180 CSV writer for report exports. String cells that start with = + - @ (or a tab/CR) are
 * prefixed with an apostrophe so spreadsheet apps cannot execute them as formulas — debtor names are
 * user-entered, so this is a real injection vector. Non-String cells (numbers, enums, dates) are written
 * as-is so negative amounts stay numeric. Output starts with a UTF-8 BOM so Excel reads accents correctly.
 */
public final class CsvSupport {

    private final StringBuilder sb = new StringBuilder("\uFEFF");

    public CsvSupport row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        sb.append("\r\n");
        return this;
    }

    public byte[] toBytes() {
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(Object v) {
        if (v == null) return "";
        String s = v instanceof java.math.BigDecimal bd ? bd.toPlainString() : v.toString();
        if (v instanceof String && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
