package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reading a spreadsheet saved as CSV. Pure: no database. Rows are turned into a fixed column order here, so what is stored for a row and later committed is
 * exactly what was previewed. Nothing here decides that a client "is" anything; the file only supplies records, which stay unverified like any other entry.
 */
public final class ImportRules {

    private ImportRules() {}

    public static final String CLIENTS = "CLIENTS", REGISTRATIONS = "REGISTRATIONS";
    public static final String OK = "OK", DUPLICATE = "DUPLICATE", REJECTED = "REJECTED", IMPORTED = "IMPORTED";
    public static final int MAX_ROWS = 2000;
    public static final int MAX_BYTES = 2_000_000;
    public static final char SEP = '\u001F';

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("d/M/uuuu");

    /** Column order for a kind. The first ones are required. */
    public static List<String> columns(String kind) {
        return switch (kind) {
            case CLIENTS -> List.of("name", "email", "phone", "notes");
            case REGISTRATIONS -> List.of("client", "authority", "type", "number", "issued", "expiry", "notes");
            default -> throw new IllegalArgumentException("Choose clients or registrations");
        };
    }

    public static int required(String kind) { return CLIENTS.equals(kind) ? 1 : 3; }

    public static String norm(String header) { return header == null ? "" : header.toLowerCase().replaceAll("[^a-z0-9]", ""); }

    /** Aliases people actually use in headers. */
    private static String canonical(String kind, String header) {
        String h = norm(header);
        return switch (h) {
            case "clientname", "companyname", "company", "businessname" -> CLIENTS.equals(kind) ? "name" : "client";
            case "emailaddress", "contactemail" -> "email";
            case "phonenumber", "contactphone", "telephone", "cell", "mobile" -> "phone";
            case "registrationtype", "licencetype", "licensetype" -> "type";
            case "registrationnumber", "reference", "referencenumber", "regno" -> "number";
            case "issuedon", "issuedate", "issueddate", "dateissued" -> "issued";
            case "expireson", "expirydate", "expirydateon", "dateexpires", "expires" -> "expiry";
            default -> h;
        };
    }

    /** Position of each wanted column in the file's header row, or -1 when absent. Throws when a required column is missing or one appears twice. */
    public static int[] mapHeader(String kind, List<String> header) {
        List<String> cols = columns(kind);
        int[] pos = new int[cols.size()];
        java.util.Arrays.fill(pos, -1);
        for (int i = 0; i < header.size(); i++) {
            int c = cols.indexOf(canonical(kind, header.get(i)));
            if (c < 0) continue;
            if (pos[c] >= 0) throw new IllegalArgumentException("The column \"" + cols.get(c) + "\" appears more than once");
            pos[c] = i;
        }
        List<String> missing = new ArrayList<>();
        for (int c = 0; c < required(kind); c++) if (pos[c] < 0) missing.add(cols.get(c));
        if (!missing.isEmpty()) throw new IllegalArgumentException("The file needs a column for: " + String.join(", ", missing) + ". Columns found: " + String.join(", ", header));
        return pos;
    }

    /** CSV with quotes, doubled quotes inside quotes, CRLF or LF line ends, and an optional byte-order mark. Blank lines are dropped. */
    public static List<List<String>> parseCsv(String text) {
        if (text != null && text.startsWith("﻿")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) return rows;
        char delim = firstLine(text).chars().filter(c -> c == ';').count() > firstLine(text).chars().filter(c -> c == ',').count() ? ';' : ',';
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false;
                } else cell.append(ch);
            } else if (ch == '"' && cell.length() == 0) quoted = true;
            else if (ch == delim) { row.add(cell.toString()); cell.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                addRow(rows, row); row = new ArrayList<>();
            } else cell.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("A quoted value is never closed");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); addRow(rows, row); }
        return rows;
    }

    private static String firstLine(String t) { int n = t.indexOf('\n'); return n < 0 ? t : t.substring(0, n); }
    private static void addRow(List<List<String>> rows, List<String> row) { if (row.stream().anyMatch(s -> !s.isBlank())) rows.add(row); }

    /** Row cells in column order, trimmed, blanks as empty strings. A spreadsheet formula prefix is neutralised so a later export cannot run it. */
    public static List<String> arrange(int[] pos, List<String> cells) {
        List<String> out = new ArrayList<>();
        for (int p : pos) out.add(p < 0 || p >= cells.size() ? "" : safe(cells.get(p).trim()));
        return out;
    }

    static String safe(String v) { return !v.isEmpty() && "=+@".indexOf(v.charAt(0)) >= 0 ? "'" + v : v; }

    public static String join(List<String> fields) { return String.join(String.valueOf(SEP), fields); }
    public static List<String> split(String stored) { return new ArrayList<>(java.util.Arrays.asList(stored.split(String.valueOf(SEP), -1))); }

    public static String nameKey(String name) { return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(); }

    /** Returns what is wrong with a client row, or null. */
    public static String clientProblem(List<String> f) {
        if (f.get(0).isBlank()) return "The name is empty";
        if (f.get(0).length() > 255) return "The name is longer than 255 characters";
        if (!f.get(1).isBlank() && (!EMAIL.matcher(f.get(1)).matches() || f.get(1).length() > 255)) return "The email address is not valid";
        if (f.get(2).length() > 50) return "The phone number is longer than 50 characters";
        if (f.get(3).length() > 4000) return "The notes are longer than 4000 characters";
        return null;
    }

    /** Returns what is wrong with a registration row apart from finding the client, or null. */
    public static String registrationProblem(List<String> f) {
        if (f.get(0).isBlank()) return "The client is empty";
        if (f.get(1).isBlank()) return "The authority is empty";
        if (f.get(1).length() > 50) return "The authority is longer than 50 characters";
        if (f.get(2).isBlank()) return "The registration type is empty";
        if (f.get(2).length() > 100) return "The registration type is longer than 100 characters";
        if (f.get(3).length() > 100) return "The number is longer than 100 characters";
        LocalDate issued, expiry;
        try { issued = date(f.get(4)); } catch (IllegalArgumentException e) { return "The issued date is not a date (use yyyy-mm-dd or dd/mm/yyyy)"; }
        try { expiry = date(f.get(5)); } catch (IllegalArgumentException e) { return "The expiry date is not a date (use yyyy-mm-dd or dd/mm/yyyy)"; }
        if (issued != null && expiry != null && expiry.isBefore(issued)) return "The expiry date is before the issued date";
        return null;
    }

    /** Blank is no date. Accepts yyyy-mm-dd and dd/mm/yyyy, nothing else. */
    public static LocalDate date(String v) {
        if (v == null || v.isBlank()) return null;
        try { return v.contains("/") ? LocalDate.parse(v.trim(), SLASH) : LocalDate.parse(v.trim()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Not a date: " + v); }
    }

    /** The stored form of a registration row has ISO dates, so what was previewed is committed unchanged. */
    public static List<String> isoDates(List<String> f) {
        List<String> out = new ArrayList<>(f);
        LocalDate i = date(f.get(4)), e = date(f.get(5));
        out.set(4, i == null ? "" : i.toString());
        out.set(5, e == null ? "" : e.toString());
        return out;
    }

    public static String registrationKey(String clientKey, List<String> f) {
        return clientKey + "|" + f.get(1).trim().toUpperCase() + "|" + f.get(2).trim().toLowerCase() + "|" + f.get(3).trim().toLowerCase();
    }

    public static String discardProblem(String note) { return note == null || note.trim().length() < 10 ? "Give a reason of at least 10 characters" : null; }
}
