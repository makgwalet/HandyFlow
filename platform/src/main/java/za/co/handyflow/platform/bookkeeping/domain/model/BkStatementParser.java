package za.co.handyflow.platform.bookkeeping.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a bank statement CSV (Date, Description, Reference, Amount) and reports every bad line by
 * its line number, in plain words. Nothing is imported unless the whole file reads cleanly.
 */
public final class BkStatementParser {

    public record Row(int line, LocalDate date, String description, String reference, BigDecimal amount) {}

    /** The file had problems; {@link #getMessage()} is safe to show on screen. */
    public static final class StatementException extends RuntimeException {
        private final List<String> problems;
        public StatementException(List<String> problems, int total) {
            super(buildMessage(problems, total));
            this.problems = List.copyOf(problems);
        }
        public List<String> getProblems() { return problems; }
        private static String buildMessage(List<String> p, int total) {
            int shown = Math.min(p.size(), MAX_SHOWN);
            String head = String.join(" ", p.subList(0, shown));
            String more = p.size() > shown ? " (and " + (p.size() - shown) + " more)" : "";
            return "Nothing was imported. " + head + more;
        }
    }

    public static final int MAX_SHOWN = 5;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter YMD_SLASH = DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    private BkStatementParser() {}

    public static List<Row> parse(String csv) {
        String[] lines = csv.replace("﻿", "").split("\\r?\\n");
        List<Row> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) { // line 1 is the header
            String raw = lines[i].trim();
            if (raw.isEmpty()) continue;
            int lineNo = i + 1;
            List<String> cols = splitCsv(raw);
            if (cols.size() < 4) {
                problems.add("Line " + lineNo + ": expected Date, Description, Reference, Amount.");
                continue;
            }
            LocalDate date = parseDate(cols.get(0).trim());
            if (date == null) {
                problems.add("Line " + lineNo + ": '" + clip(cols.get(0)) + "' is not a real date. Use YYYY-MM-DD or DD/MM/YYYY.");
                continue;
            }
            BigDecimal amount = parseAmount(cols.get(3));
            if (amount == null) {
                problems.add("Line " + lineNo + ": '" + clip(cols.get(3)) + "' is not an amount. Use digits with a dot, like -1250.50.");
                continue;
            }
            if (amount.abs().compareTo(MAX_AMOUNT) >= 0) {
                problems.add("Line " + lineNo + ": the amount is too large. Check it.");
                continue;
            }
            rows.add(new Row(lineNo, date, cols.get(1).trim(), cols.get(2).trim(), amount));
        }
        if (!problems.isEmpty()) throw new StatementException(problems, rows.size() + problems.size());
        return rows;
    }

    static LocalDate parseDate(String s) {
        for (DateTimeFormatter f : new DateTimeFormatter[]{ISO, DMY, YMD_SLASH}) {
            try { return LocalDate.parse(s, f); } catch (java.time.format.DateTimeParseException ignored) { /* try next */ }
        }
        return null;
    }

    static BigDecimal parseAmount(String s) {
        String t = s.trim().replace(" ", "").replace(" ", "");
        if (t.isEmpty()) return null;
        try { return new BigDecimal(t); } catch (NumberFormatException e) { return null; }
    }

    /** Splits on commas but keeps commas inside double quotes. */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private static String clip(String s) {
        String t = s.trim();
        return t.length() > 30 ? t.substring(0, 30) + "..." : t;
    }
}
