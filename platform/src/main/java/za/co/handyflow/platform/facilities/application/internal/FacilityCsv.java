package za.co.handyflow.platform.facilities.application.internal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimal RFC 4180 writer for report exports. Text that a spreadsheet would run as a formula (starts with = + - @ or a
 * control character) is prefixed with an apostrophe so exported free text (descriptions, notes, names) cannot execute
 * when the file is opened. Numbers are written as given, so negatives stay numeric. Output starts with a UTF-8 byte
 * order mark so Excel reads accented names correctly, and lines end with CRLF.
 */
public final class FacilityCsv {

    private static final String BOM = "﻿";

    private FacilityCsv() {}

    public static String build(List<String> header, List<? extends List<?>> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        appendRow(sb, header);
        for (List<?> r : rows) appendRow(sb, r);
        return sb.toString();
    }

    static String cell(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal d) return d.toPlainString();
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        String s = v.toString();
        if (!s.isEmpty()) {
            char c = s.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static void appendRow(StringBuilder sb, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
    }
}
