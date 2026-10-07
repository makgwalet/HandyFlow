package za.co.handyflow.platform.compliancetender.application.internal;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a supplier's price list saved as CSV (what Excel produces) into rate rows. Pure: no database, no clock.
 * <p>
 * It is forgiving about what suppliers actually send: a leading byte-order mark, comma, semicolon or tab separators (South African Excel often uses semicolons), quoted
 * fields with commas or line breaks inside, header names in several spellings ("Unit Price", "Rate", "Cost"), amounts written "R 1 250,50" or "1,250.50". It is strict about
 * what would be guessing: an amount with more than two decimals, a negative amount or an unknown category is reported against its line and the row is skipped, never rounded
 * or corrected.
 */
public final class RateCsvParser {

    private RateCsvParser() {}

    /** {@code category} is null when the file gave none. */
    public record Row(int line, String category, String itemRef, String description, String unit, BigDecimal unitCost, String supplier, String notes) {}

    public record Problem(int line, String message) {}

    /** {@code fatal} is set when the file as a whole cannot be read (no header, or no description or cost column); rows are then empty. */
    public record Parsed(List<Row> rows, List<Problem> problems, String fatal) {}

    private static final Map<String, String> CATEGORY_WORDS = Map.ofEntries(
            Map.entry("material", "MATERIAL"), Map.entry("materials", "MATERIAL"),
            Map.entry("labour", "LABOUR"), Map.entry("labor", "LABOUR"),
            Map.entry("plant", "PLANT"), Map.entry("equipment", "PLANT"),
            Map.entry("subcontract", "SUBCONTRACT"), Map.entry("subcontractor", "SUBCONTRACT"), Map.entry("subcontractors", "SUBCONTRACT"),
            Map.entry("other", "OTHER"));

    private static final Map<String, Set<String>> HEADERS = Map.of(
            "description", Set.of("description", "item", "item description", "name", "material", "product", "details"),
            "unit", Set.of("unit", "uom", "unit of measure", "measure"),
            "cost", Set.of("unit cost", "cost", "rate", "price", "unit price", "unit rate", "cost price", "nett price", "net price"),
            "category", Set.of("category", "type", "class"),
            "supplier", Set.of("supplier", "vendor", "supplier name"),
            "ref", Set.of("code", "ref", "item ref", "item code", "sku", "part number", "part no", "reference", "item no"),
            "notes", Set.of("notes", "note", "comment", "comments", "remarks"));

    /** The category word as the library stores it, or null when it is not one we know. Blank is the caller's default, not an error, so it is handled before this. */
    public static String category(String word) {
        return word == null ? null : CATEGORY_WORDS.get(word.trim().toLowerCase(Locale.ROOT));
    }

    /** The category a new rate takes when the file does not give one: what the caller chose, else Material. */
    public static String defaultCategory(String chosen) {
        String c = chosen == null || chosen.isBlank() ? null : category(chosen);
        return c == null ? "MATERIAL" : c;
    }

    public static Parsed parse(String text, String defaultCategory) {
        List<Problem> problems = new ArrayList<>();
        if (text == null || text.isBlank()) return new Parsed(List.of(), problems, "The file is empty.");
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        char delimiter = detectDelimiter(body);
        List<List<String>> records = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();
        tokenize(body, delimiter, records, lineNumbers);

        int headerAt = -1;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).stream().anyMatch(c -> !c.isBlank())) { headerAt = i; break; }
        }
        if (headerAt < 0) return new Parsed(List.of(), problems, "The file is empty.");

        Map<String, Integer> col = new HashMap<>();
        List<String> header = records.get(headerAt);
        for (int c = 0; c < header.size(); c++) {
            String h = normaliseHeader(header.get(c));
            for (var e : HEADERS.entrySet()) {
                if (e.getValue().contains(h) && !col.containsKey(e.getKey())) col.put(e.getKey(), c);
            }
        }
        if (!col.containsKey("description")) return new Parsed(List.of(), problems, "No description column found. The first line must name the columns, e.g. Description, Unit, Unit Cost.");
        if (!col.containsKey("cost")) return new Parsed(List.of(), problems, "No cost column found. Name it Unit Cost, Cost, Rate or Price.");

        if (defaultCategory != null && !defaultCategory.isBlank() && category(defaultCategory) == null) {
            return new Parsed(List.of(), problems, "Unknown default category: " + defaultCategory);
        }

        List<Row> rows = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = headerAt + 1; i < records.size(); i++) {
            List<String> r = records.get(i);
            if (r.stream().allMatch(String::isBlank)) continue;
            int line = lineNumbers.get(i);
            String description = cell(r, col.get("description"));
            if (description.isEmpty()) { problems.add(new Problem(line, "No description.")); continue; }
            if (description.length() > 500) { problems.add(new Problem(line, "Description is longer than 500 characters.")); continue; }
            String costText = cell(r, col.get("cost"));
            if (costText.isEmpty()) { problems.add(new Problem(line, "No cost for " + description + ".")); continue; }
            BigDecimal cost;
            try {
                cost = money(costText);
            } catch (IllegalArgumentException e) {
                problems.add(new Problem(line, e.getMessage() + " (" + description + ")"));
                continue;
            }
            String categoryWord = cell(r, col.get("category"));
            String category = null;       // left null when the file does not say, so the caller decides (a new rate takes the default; an existing one keeps its own)
            if (!categoryWord.isEmpty()) {
                category = category(categoryWord);
                if (category == null) { problems.add(new Problem(line, "Unknown category \"" + categoryWord + "\" (use Material, Labour, Plant, Subcontract or Other).")); continue; }
            }
            String unit = cell(r, col.get("unit"));
            String supplier = cell(r, col.get("supplier"));
            if (unit.length() > 30) { problems.add(new Problem(line, "Unit is longer than 30 characters.")); continue; }
            if (supplier.length() > 120) { problems.add(new Problem(line, "Supplier is longer than 120 characters.")); continue; }
            String ref = cell(r, col.get("ref"));
            if (ref.length() > 40) { problems.add(new Problem(line, "Code is longer than 40 characters.")); continue; }
            String notes = cell(r, col.get("notes"));
            if (notes.length() > 500) { problems.add(new Problem(line, "Notes are longer than 500 characters.")); continue; }

            String key = (description + "|" + unit + "|" + supplier).toLowerCase(Locale.ROOT);
            Integer first = seen.putIfAbsent(key, line);
            if (first != null) { problems.add(new Problem(line, description + " is listed twice (first on line " + first + "); this line was skipped.")); continue; }
            rows.add(new Row(line, category, ref.isEmpty() ? null : ref, description, unit, cost, supplier, notes.isEmpty() ? null : notes));
        }
        return new Parsed(rows, problems, null);
    }

    /**
     * "R 1 250,50", "1,250.50", "1250.5", "R1250" all read as an amount. A lone comma is a decimal comma unless it groups thousands (1,250); with both, the last separator
     * is the decimal one. More than two decimals, a negative amount and anything else is refused.
     */
    public static BigDecimal money(String raw) {
        String s = raw.trim().replace(' ', ' ').replaceAll("(?i)^(zar|r)\\s*", "").replace(" ", "");
        if (s.isEmpty()) throw new IllegalArgumentException("No cost.");
        if (s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"))) throw new IllegalArgumentException("A cost cannot be negative: \"" + raw.trim() + "\".");
        int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            char decimal = comma > dot ? ',' : '.';
            char group = decimal == ',' ? '.' : ',';
            s = s.replace(String.valueOf(group), "").replace(decimal, '.');
        } else if (comma >= 0) {
            s = s.matches("\\d{1,3}(,\\d{3})+") ? s.replace(",", "") : s.replace(',', '.');
        }
        if (!s.matches("\\d+(\\.\\d+)?")) throw new IllegalArgumentException("\"" + raw.trim() + "\" is not an amount.");
        BigDecimal v = new BigDecimal(s);
        if (v.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("\"" + raw.trim() + "\" has more than 2 decimal places.");
        if (v.compareTo(new BigDecimal("9999999999999")) > 0) throw new IllegalArgumentException("\"" + raw.trim() + "\" is too large.");
        return v.setScale(2);
    }

    private static String cell(List<String> row, Integer index) {
        if (index == null || index >= row.size()) return "";
        return row.get(index).trim().replaceAll("\\s+", " ");
    }

    private static String normaliseHeader(String h) {
        return h == null ? "" : h.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** The separator that appears most on the first non-blank line outside quotes; a comma when none does. */
    private static char detectDelimiter(String body) {
        int comma = 0, semi = 0, tab = 0;
        boolean quoted = false;
        boolean started = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && (c == '\n' || c == '\r')) { if (started) break; }
            else if (!quoted) {
                if (!Character.isWhitespace(c)) started = true;
                if (c == ',') comma++; else if (c == ';') semi++; else if (c == '\t') tab++;
            }
        }
        if (semi > comma && semi >= tab) return ';';
        if (tab > comma && tab > semi) return '\t';
        return ',';
    }

    /** Splits into records and fields, honouring quotes ("" is a quote, and a quoted field may hold the delimiter or a line break). {@code lineNumbers} gives each record's first line. */
    private static void tokenize(String body, char delimiter, List<List<String>> records, List<Integer> lineNumbers) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1, recordLine = 1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < body.length() && body.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else quoted = false;
                } else {
                    if (c == '\n') line++;
                    field.append(c);
                }
            } else if (c == '"' && field.toString().isBlank()) {
                quoted = true;
                field.setLength(0);
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') i++;
                fields.add(field.toString());
                field.setLength(0);
                records.add(fields);
                lineNumbers.add(recordLine);
                fields = new ArrayList<>();
                line++;
                recordLine = line;
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(fields);
            lineNumbers.add(recordLine);
        }
    }
}
