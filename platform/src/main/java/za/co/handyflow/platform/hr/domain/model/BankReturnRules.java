package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Reading what the bank sent back about a salary file, and matching it to the run's payments. Pure.
 * <p>
 * No bank publishes one return layout that every account holder gets, so this reads the report most business banking sites let you export as
 * CSV (unpaid or rejected items, or statement lines): a first row of headings, one row per item. Headings are recognised by name (account,
 * amount, reason or description, reference, name, branch), in any order, with a comma, semicolon or tab between columns. A row is matched to
 * a payment of the run by account number and amount to the cent; nothing is changed until a person has seen the matches and confirmed.
 */
public final class BankReturnRules {

    public static final int MAX_ROWS = 5000, MAX_CHARS = 1_000_000;
    public static final String MATCHED = "MATCHED", AMBIGUOUS = "AMBIGUOUS", UNMATCHED = "UNMATCHED", DUPLICATE = "DUPLICATE", ALREADY_RETURNED = "ALREADY_RETURNED";
    public static final String DEFAULT_REASON = "Returned by the bank (no reason given in the file)";

    private BankReturnRules() {}

    /** One row of the bank's file. {@code line} is its line number in the file. */
    public record Row(int line, String account, BigDecimal amount, String reason, String reference, String name) {}

    /** A payment of the run as far as matching is concerned. */
    public record Candidate(UUID id, String account, BigDecimal amount, String reference, String name, String status) {}

    public record Match(Row row, String outcome, UUID paymentId) {}

    public record Parsed(List<Row> rows, String problem) {}

    /** Reads the file. A problem means nothing could be read, and says why. */
    public static Parsed parse(String text) {
        if (text == null || text.isBlank()) return new Parsed(List.of(), "The file is empty.");
        if (text.length() > MAX_CHARS) return new Parsed(List.of(), "The file is too large to read here.");
        String clean = text.startsWith("﻿") ? text.substring(1) : text;
        String[] lines = clean.split("\\r?\\n|\\r");
        int headerAt = -1;
        char sep = ',';
        for (int i = 0; i < lines.length && i < 20; i++) {
            if (lines[i].isBlank()) continue;
            char s = separator(lines[i]);
            List<String> cells = split(lines[i], s);
            if (column(cells, ACCOUNT) >= 0 && column(cells, AMOUNT) >= 0) { headerAt = i; sep = s; break; }
        }
        if (headerAt < 0) return new Parsed(List.of(), "The first row must have headings, including one for the account number and one for the amount.");
        List<String> head = split(lines[headerAt], sep);
        int cAccount = column(head, ACCOUNT), cAmount = column(head, AMOUNT), cReason = column(head, REASON), cRef = column(head, REFERENCE), cName = column(head, NAME);
        List<Row> rows = new ArrayList<>();
        for (int i = headerAt + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            if (rows.size() >= MAX_ROWS) return new Parsed(List.of(), "The file has more than " + MAX_ROWS + " rows.");
            List<String> cells = split(lines[i], sep);
            String account = digits(get(cells, cAccount));
            BigDecimal amount = amount(get(cells, cAmount));
            if (account.isEmpty() && amount == null) continue;          // a total line or a note
            rows.add(new Row(i + 1, account, amount, clip(get(cells, cReason)), clip(get(cells, cRef)), clip(get(cells, cName))));
        }
        if (rows.isEmpty()) return new Parsed(List.of(), "The file has headings but no rows.");
        return new Parsed(rows, null);
    }

    /**
     * Matches each row to one payment: the same account (leading zeros do not matter) and the same amount. When several payments fit, the
     * reference and then the name decide. A payment is matched at most once; a payment that is already returned is reported as such.
     */
    public static List<Match> match(List<Row> rows, List<Candidate> payments) {
        List<Match> out = new ArrayList<>();
        Set<UUID> taken = new java.util.HashSet<>();
        for (Row r : rows) {
            if (r.amount() == null || r.account().isEmpty()) { out.add(new Match(r, UNMATCHED, null)); continue; }
            List<Candidate> fit = payments.stream()
                    .filter(p -> sameAccount(p.account(), r.account()) && p.amount().compareTo(money(r.amount())) == 0).toList();
            if (fit.isEmpty()) { out.add(new Match(r, UNMATCHED, null)); continue; }
            List<Candidate> open = fit.stream().filter(p -> !PayrollPaymentRules.RETURNED.equals(p.status())).toList();
            if (open.isEmpty()) { out.add(new Match(r, ALREADY_RETURNED, fit.get(0).id())); continue; }
            List<Candidate> free = open.stream().filter(p -> !taken.contains(p.id())).toList();
            if (free.isEmpty()) { out.add(new Match(r, DUPLICATE, open.get(0).id())); continue; }
            List<Candidate> pick = free;
            if (pick.size() > 1) pick = narrow(pick, c -> same(c.reference(), r.reference()));
            if (pick.size() > 1) pick = narrow(pick, c -> same(c.name(), r.name()));
            if (pick.size() != 1) { out.add(new Match(r, AMBIGUOUS, null)); continue; }
            taken.add(pick.get(0).id());
            out.add(new Match(r, MATCHED, pick.get(0).id()));
        }
        return out;
    }

    public static String reasonFor(Row r) {
        return r.reason() == null || r.reason().isBlank() ? DEFAULT_REASON : r.reason();
    }

    // ── reading helpers ──

    private static final List<String> ACCOUNT = List.of("account number", "account no", "account", "acc no", "acc number", "to account", "beneficiary account", "accountnumber");
    private static final List<String> AMOUNT = List.of("amount", "transaction amount", "value", "rand value", "debit");
    private static final List<String> REASON = List.of("reason", "reject reason", "rejection reason", "return reason", "error", "status", "message", "description", "reason code", "narrative");
    private static final List<String> REFERENCE = List.of("reference", "beneficiary reference", "beneficiary description", "your reference", "payment reference", "ref");
    private static final List<String> NAME = List.of("name", "beneficiary name", "account name", "beneficiary", "to account name");

    /** Position of the first heading equal to one of the names, trying the names in order. -1 when none. */
    static int column(List<String> head, List<String> names) {
        for (String n : names) for (int i = 0; i < head.size(); i++) if (head.get(i).trim().toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ").equals(n)) return i;
        return -1;
    }

    private static char separator(String line) {
        int tabs = count(line, '\t'), semis = count(line, ';'), commas = count(line, ',');
        return tabs > 0 && tabs >= semis && tabs >= commas ? '\t' : semis > commas ? ';' : ',';
    }

    private static int count(String s, char c) { int n = 0; for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++; return n; }

    /** Splits on the separator, keeping separators inside double quotes. */
    static List<String> split(String line, char sep) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == sep && !quoted) { cells.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(c);
        }
        cells.add(cur.toString().trim());
        return cells;
    }

    private static String get(List<String> cells, int i) { return i < 0 || i >= cells.size() ? "" : cells.get(i); }

    private static String clip(String s) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() > PayrollPaymentRules.MAX_REASON ? t.substring(0, PayrollPaymentRules.MAX_REASON) : t;
    }

    private static String digits(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }

    /** "R 1 234,56", "1,234.56", "-1234.56" and "(1234.56)" are all 1234.56; anything else is null. The sign does not matter. */
    static BigDecimal amount(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[Rr]", "").replaceAll("[\\s ]", "").replace("(", "").replace(")", "").replace("-", "");
        if (s.isEmpty() || !s.matches("[0-9.,]+")) return null;
        int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
        if (comma > dot) s = s.replace(".", "").replace(',', '.');            // 1.234,56
        else s = s.replace(",", "");                                           // 1,234.56
        try { return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private static boolean sameAccount(String a, String b) {
        String x = stripZeros(digits(a)), y = stripZeros(digits(b));
        return !x.isEmpty() && x.equals(y);
    }

    private static String stripZeros(String s) { return s.replaceFirst("^0+", ""); }

    private static boolean same(String a, String b) {
        String x = a == null ? "" : a.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT), y = b == null ? "" : b.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return !x.isEmpty() && x.equals(y);
    }

    private static List<Candidate> narrow(List<Candidate> from, java.util.function.Predicate<Candidate> test) {
        List<Candidate> hit = from.stream().filter(test).toList();
        return hit.isEmpty() ? from : hit;
    }
}
