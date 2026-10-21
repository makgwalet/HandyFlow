package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.util.*;

/**
 * The rules of importing a catalogue file, with no database in them so they can be tested on their own.
 * <p>
 * A file is read into rows, each row is checked against the items that already exist, and the result is a plan: for every
 * row, will it create an item, update one, change nothing, or be refused and why. The dry run shows that plan; the real
 * import applies the same plan, so what was shown is what happens.
 * Items are matched by SKU. Cells left empty on an update mean "leave as it is".
 */
public final class ImportRules {

    private ImportRules() {}

    public static final int MAX_ROWS = 2000;
    public static final int MAX_CHARS = 2_000_000;

    public enum Mode { CREATE_ONLY, UPDATE_ONLY, CREATE_OR_UPDATE }

    public enum Action { CREATE, UPDATE, UNCHANGED, ERROR }

    /** The columns of the template and the export, in order. */
    public static final List<String> COLUMNS = List.of("sku", "name", "description", "category", "unit", "brand", "barcode", "type", "status", "price", "cost", "vat_rate", "vat_exempt");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("sku", "sku"), Map.entry("code", "sku"), Map.entry("item code", "sku"), Map.entry("product code", "sku"),
            Map.entry("name", "name"), Map.entry("item name", "name"), Map.entry("product name", "name"), Map.entry("item", "name"),
            Map.entry("description", "description"),
            Map.entry("category", "category"), Map.entry("category path", "category"),
            Map.entry("unit", "unit"), Map.entry("uom", "unit"), Map.entry("unit of measure", "unit"),
            Map.entry("brand", "brand"), Map.entry("barcode", "barcode"), Map.entry("ean", "barcode"),
            Map.entry("type", "type"), Map.entry("item type", "type"), Map.entry("status", "status"),
            Map.entry("price", "price"), Map.entry("selling price", "price"), Map.entry("default price", "price"), Map.entry("retail price", "price"),
            Map.entry("cost", "cost"), Map.entry("cost price", "cost"),
            Map.entry("vat_rate", "vat_rate"), Map.entry("vat rate", "vat_rate"), Map.entry("vat %", "vat_rate"), Map.entry("vat", "vat_rate"),
            Map.entry("vat_exempt", "vat_exempt"), Map.entry("vat exempt", "vat_exempt"));

    // ── Reading the file ───────────────────────────────────────────────────────

    /** One data row as typed: column name to text, plus its line number in the file (the header is line 1). */
    public record RawRow(int line, Map<String, String> cells) {}

    public record Table(List<RawRow> rows, List<String> unknownColumns, List<String> problems) {}

    /** Splits CSV text into records. Understands quotes, doubled quotes, line breaks inside quotes and , ; or tab separators. */
    public static List<List<String>> parseCsv(String text) {
        List<List<String>> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        if (text.charAt(0) == '﻿') text = text.substring(1);
        char sep = separator(text);
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false;
                } else cell.append(c);
            } else if (c == '"' && cell.length() == 0) { quoted = true; any = true; }
            else if (c == sep) { row.add(cell.toString()); cell.setLength(0); any = true; }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if (any || row.size() > 1 || !row.get(0).isEmpty()) out.add(row);
                row = new ArrayList<>(); any = false;
            } else { cell.append(c); any = true; }
        }
        row.add(cell.toString());
        if (any || row.size() > 1 || !row.get(0).isEmpty()) out.add(row);
        return out;
    }

    private static char separator(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        int commas = 0, semis = 0, tabs = 0;
        boolean q = false;
        for (char c : first.toCharArray()) {
            if (c == '"') q = !q;
            else if (!q) { if (c == ',') commas++; else if (c == ';') semis++; else if (c == '\t') tabs++; }
        }
        return tabs > commas && tabs > semis ? '\t' : semis > commas ? ';' : ',';
    }

    /** Reads the file: finds the columns by name (case and spacing do not matter) and keeps the non-empty rows. */
    public static Table read(String text) {
        List<String> problems = new ArrayList<>();
        if (text == null || text.isBlank()) return new Table(List.of(), List.of(), List.of("The file is empty."));
        if (text.length() > MAX_CHARS) return new Table(List.of(), List.of(), List.of("The file is too large. Split it into files of up to " + MAX_ROWS + " rows."));
        List<List<String>> records = parseCsv(text);
        if (records.isEmpty()) return new Table(List.of(), List.of(), List.of("The file is empty."));
        List<String> header = records.get(0);
        String[] columns = new String[header.size()];
        List<String> unknown = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).trim().toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ");
            String col = ALIASES.get(h);
            if (col == null) col = ALIASES.get(h.replace(' ', '_'));
            if (col == null) { if (!h.isEmpty()) unknown.add(header.get(i).trim()); continue; }
            if (!seen.add(col)) { problems.add("The column '" + col + "' appears twice."); continue; }
            columns[i] = col;
        }
        if (!seen.contains("sku") && !seen.contains("name")) problems.add("The file needs a 'sku' or a 'name' column in its first line.");
        if (records.size() - 1 > MAX_ROWS) problems.add("The file has " + (records.size() - 1) + " rows; the limit is " + MAX_ROWS + ". Split it into smaller files.");
        List<RawRow> rows = new ArrayList<>();
        if (problems.isEmpty()) {
            for (int r = 1; r < records.size(); r++) {
                List<String> rec = records.get(r);
                Map<String, String> cells = new LinkedHashMap<>();
                boolean blank = true;
                for (int i = 0; i < rec.size() && i < columns.length; i++) {
                    if (columns[i] == null) continue;
                    String v = unquoteFormulaGuard(rec.get(i).trim());
                    if (!v.isEmpty()) blank = false;
                    cells.put(columns[i], v);
                }
                if (!blank) rows.add(new RawRow(r + 1, cells));
            }
            if (rows.isEmpty()) problems.add("The file has a header but no rows.");
        }
        return new Table(rows, unknown, problems);
    }

    /** Exports prefix text that starts with = + @ - with an apostrophe so spreadsheets do not run it as a formula; undo that here. */
    private static String unquoteFormulaGuard(String v) {
        return v.length() > 1 && v.charAt(0) == '\'' && "=+@-".indexOf(v.charAt(1)) >= 0 ? v.substring(1) : v;
    }

    // ── Existing items, as the plan needs them ─────────────────────────────────

    public record Existing(UUID id, String sku, String name, String description, String categoryPath, String unit, String brand, String barcode,
                           CatalogueItemType type, CatalogueItemStatus status, BigDecimal price, BigDecimal cost, BigDecimal vatRate, boolean vatExempt,
                           long version, boolean hasVariants, boolean isVariant) {}

    /** What the importing user and the tenant allow. */
    public record Options(boolean priceApprovalRequired, boolean canWriteCost, boolean newItemsMustStartAsDraft) {}

    /** A row after reading: the values to apply (null = not given) and the problems found in the cells. */
    public record Parsed(int line, String sku, String name, String description, String category, String unit, String brand, String barcode,
                         CatalogueItemType type, CatalogueItemStatus status, BigDecimal price, BigDecimal cost, BigDecimal vatRate, Boolean vatExempt,
                         List<String> errors) {}

    public record Planned(int line, String key, Action action, UUID itemId, Long version, List<String> changes, List<String> errors, List<String> notes, Parsed row) {}

    public record Plan(List<Planned> rows) {
        public long count(Action a) { return rows.stream().filter(r -> r.action() == a).count(); }
        public boolean hasErrors() { return count(Action.ERROR) > 0; }
    }

    public static Parsed parse(RawRow raw) {
        Map<String, String> c = raw.cells();
        List<String> errors = new ArrayList<>();
        BigDecimal price = money(c.get("price"), "price", errors);
        BigDecimal cost = money(c.get("cost"), "cost", errors);
        BigDecimal vat = decimal(c.get("vat_rate"), "vat_rate", errors, new BigDecimal("100"));
        Boolean exempt = bool(c.get("vat_exempt"), "vat_exempt", errors);
        CatalogueItemType type = null;
        String t = blankToNull(c.get("type"));
        if (t != null) {
            type = itemType(t);
            if (type == null) errors.add("Type '" + t + "' is not one of: " + String.join(", ", Arrays.stream(CatalogueItemType.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).toList()) + ".");
        }
        CatalogueItemStatus status = null;
        String s = blankToNull(c.get("status"));
        if (s != null) {
            try { status = CatalogueItemStatus.valueOf(s.toUpperCase(Locale.ROOT).replace(' ', '_')); }
            catch (IllegalArgumentException e) { errors.add("Status '" + s + "' is not one of: draft, active, suspended, discontinued, archived."); }
        }
        String sku = blankToNull(c.get("sku"));
        if (sku != null) sku = sku.toUpperCase(Locale.ROOT);
        return new Parsed(raw.line(), sku, blankToNull(c.get("name")), blankToNull(c.get("description")), blankToNull(c.get("category")), blankToNull(c.get("unit")),
                blankToNull(c.get("brand")), blankToNull(c.get("barcode")), type, status, price, cost, vat, exempt, errors);
    }

    private static CatalogueItemType itemType(String t) {
        String n = t.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (CatalogueItemType x : CatalogueItemType.values()) if (x.name().equals(n)) return x;
        return null;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** "1 250,50", "R1250.5" and "12,5" are read as money; more than two decimals or a negative number is an error. */
    public static BigDecimal money(String text, String field, List<String> errors) {
        BigDecimal v = decimal(text, field, errors, null);
        if (v == null) return null;
        if (v.signum() < 0) { errors.add("The " + field + " cannot be negative."); return null; }
        if (v.stripTrailingZeros().scale() > 2) { errors.add("The " + field + " '" + text.trim() + "' has more than two decimals."); return null; }
        return v.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public static BigDecimal decimal(String text, String field, List<String> errors, BigDecimal max) {
        String s = blankToNull(text);
        if (s == null) return null;
        s = s.replace("R", "").replace("r", "").replace("%", "").replace(" ", "").replace(" ", "");
        if (s.indexOf(',') >= 0 && s.indexOf('.') < 0) s = s.replace(',', '.');
        else s = s.replace(",", "");
        try {
            BigDecimal v = new BigDecimal(s);
            if (max != null && v.compareTo(max) > 0) { errors.add("The " + field + " '" + text.trim() + "' is above " + max.toPlainString() + "."); return null; }
            if (max != null && v.signum() < 0) { errors.add("The " + field + " cannot be negative."); return null; }
            return v;
        } catch (NumberFormatException e) {
            errors.add("The " + field + " '" + text.trim() + "' is not a number.");
            return null;
        }
    }

    public static Boolean bool(String text, String field, List<String> errors) {
        String s = blankToNull(text);
        if (s == null) return null;
        switch (s.toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true", "1" -> { return Boolean.TRUE; }
            case "no", "n", "false", "0" -> { return Boolean.FALSE; }
            default -> { errors.add("The " + field + " '" + s + "' should be yes or no."); return null; }
        }
    }

    // ── The plan ───────────────────────────────────────────────────────────────

    /**
     * @param categoryPaths every category path ("Hardware > Cement") of the tenant; a cell may also hold just the last name
     *                      when only one category has that name
     */
    public static Plan plan(List<Parsed> rows, Mode mode, Collection<Existing> existing, Collection<String> categoryPaths, Options opt) {
        Map<String, Existing> bySku = new HashMap<>();
        Map<String, Existing> byName = new HashMap<>();
        Map<String, Existing> byBarcode = new HashMap<>();
        for (Existing e : existing) {
            if (e.sku() != null) bySku.put(e.sku().toLowerCase(Locale.ROOT), e);
            byName.put(e.name().toLowerCase(Locale.ROOT), e);
            if (e.barcode() != null) byBarcode.put(e.barcode().toLowerCase(Locale.ROOT), e);
        }
        Map<String, String> categoryByLower = new HashMap<>();
        Map<String, List<String>> categoryByLeaf = new HashMap<>();
        for (String p : categoryPaths) {
            categoryByLower.put(normPath(p), p);
            String leaf = p.contains(">") ? p.substring(p.lastIndexOf('>') + 1).trim() : p;
            categoryByLeaf.computeIfAbsent(leaf.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
        }
        Map<String, Integer> skuLine = new HashMap<>(), nameLine = new HashMap<>(), barcodeLine = new HashMap<>();
        List<Planned> out = new ArrayList<>();
        for (Parsed r : rows) {
            List<String> errors = new ArrayList<>(r.errors());
            List<String> notes = new ArrayList<>();
            List<String> changes = new ArrayList<>();
            Existing match = r.sku() == null ? null : bySku.get(r.sku().toLowerCase(Locale.ROOT));
            String key = r.sku() != null ? r.sku() : r.name() != null ? r.name() : "row " + r.line();

            // within the file
            if (r.sku() != null && skuLine.putIfAbsent(r.sku().toLowerCase(Locale.ROOT), r.line()) != null)
                errors.add("The SKU '" + r.sku() + "' is already on row " + skuLine.get(r.sku().toLowerCase(Locale.ROOT)) + " of this file.");
            if (r.name() != null && nameLine.putIfAbsent(r.name().toLowerCase(Locale.ROOT), r.line()) != null)
                errors.add("The name '" + r.name() + "' is already on row " + nameLine.get(r.name().toLowerCase(Locale.ROOT)) + " of this file.");
            if (r.barcode() != null && barcodeLine.putIfAbsent(r.barcode().toLowerCase(Locale.ROOT), r.line()) != null)
                errors.add("The barcode '" + r.barcode() + "' is already on row " + barcodeLine.get(r.barcode().toLowerCase(Locale.ROOT)) + " of this file.");

            String category = null;
            if (r.category() != null) {
                category = resolveCategory(r.category(), categoryByLower, categoryByLeaf, errors);
            }
            if (r.cost() != null && !opt.canWriteCost()) errors.add("You are not allowed to set cost prices; remove the cost column.");

            if (match == null) {
                if (mode == Mode.UPDATE_ONLY) errors.add(r.sku() == null ? "Updates need a SKU." : "No item has the SKU '" + r.sku() + "'.");
                else {
                    if (r.name() == null) errors.add("A new item needs a name.");
                    if (r.price() == null && r.errors().stream().noneMatch(e -> e.startsWith("The price"))) errors.add("A new item needs a price.");
                    Existing byN = r.name() == null ? null : byName.get(r.name().toLowerCase(Locale.ROOT));
                    if (byN != null) errors.add("An item named '" + r.name() + "' already exists" + (byN.sku() != null ? " (SKU " + byN.sku() + ")" : "") + ". Use its SKU to update it.");
                    if (r.barcode() != null && byBarcode.containsKey(r.barcode().toLowerCase(Locale.ROOT)))
                        errors.add("Another item already uses the barcode '" + r.barcode() + "'.");
                    CatalogueItemStatus st = r.status();
                    if (st != null && st != CatalogueItemStatus.DRAFT && st != CatalogueItemStatus.ACTIVE) errors.add("A new item can only start as draft or active.");
                    if (st == CatalogueItemStatus.ACTIVE && opt.newItemsMustStartAsDraft()) errors.add("New items must start as drafts and be submitted for approval.");
                    if (opt.newItemsMustStartAsDraft() && st == null) notes.add("Will be created as a draft, waiting for approval.");
                }
                out.add(new Planned(r.line(), key, errors.isEmpty() ? Action.CREATE : Action.ERROR, null, null, errors.isEmpty() ? List.of("new item") : changes, errors, notes, r));
                continue;
            }

            // an existing item
            if (mode == Mode.CREATE_ONLY) errors.add("An item with the SKU '" + r.sku() + "' already exists.");
            boolean variant = match.isVariant();
            if (variant) notes.add("This is a variant: its name, category, unit, brand, type and VAT follow its main item, so those columns are ignored.");
            if (!variant && r.name() != null && !r.name().equalsIgnoreCase(match.name())) {
                Existing other = byName.get(r.name().toLowerCase(Locale.ROOT));
                if (other != null && !other.id().equals(match.id())) errors.add("An item named '" + r.name() + "' already exists.");
                else changes.add("name: " + match.name() + " → " + r.name());
            }
            diff(changes, "description", match.description(), r.description());
            if (!variant && category != null && !category.equalsIgnoreCase(nullToEmpty(match.categoryPath()))) changes.add("category: " + (match.categoryPath() == null ? "none" : match.categoryPath()) + " → " + category);
            if (!variant) diff(changes, "unit", match.unit(), r.unit());
            if (!variant) diff(changes, "brand", match.brand(), r.brand());
            if (r.barcode() != null && !r.barcode().equalsIgnoreCase(nullToEmpty(match.barcode()))) {
                Existing other = byBarcode.get(r.barcode().toLowerCase(Locale.ROOT));
                if (other != null && !other.id().equals(match.id())) errors.add("Another item already uses the barcode '" + r.barcode() + "'.");
                else changes.add("barcode: " + nullToEmpty(match.barcode()) + " → " + r.barcode());
            }
            if (!variant && r.type() != null && r.type() != match.type()) changes.add("type: " + match.type().name().toLowerCase(Locale.ROOT) + " → " + r.type().name().toLowerCase(Locale.ROOT));
            if (r.status() != null && r.status() != match.status()) notes.add("The status column is ignored on existing items; use Change status.");
            if (r.price() != null && match.price().compareTo(r.price()) != 0) {
                if (opt.priceApprovalRequired()) errors.add("Price changes need approval, so they cannot be imported. Remove the price or use Pricing / Change prices.");
                else if (match.hasVariants()) errors.add("This item is sold through its variants; set the price on a variant.");
                else changes.add("price: " + match.price().toPlainString() + " → " + r.price().toPlainString());
            }
            if (r.cost() != null && opt.canWriteCost() && (match.cost() == null || match.cost().compareTo(r.cost()) != 0))
                changes.add("cost: " + (match.cost() == null ? "none" : match.cost().toPlainString()) + " → " + r.cost().toPlainString());
            if (!variant && r.vatRate() != null && (match.vatRate() == null || match.vatRate().compareTo(r.vatRate()) != 0)) changes.add("vat rate: " + (match.vatRate() == null ? "none" : match.vatRate().toPlainString()) + " → " + r.vatRate().toPlainString());
            if (!variant && r.vatExempt() != null && r.vatExempt() != match.vatExempt()) changes.add("vat exempt: " + (match.vatExempt() ? "yes" : "no") + " → " + (r.vatExempt() ? "yes" : "no"));

            Action action = !errors.isEmpty() ? Action.ERROR : changes.isEmpty() ? Action.UNCHANGED : Action.UPDATE;
            out.add(new Planned(r.line(), key, action, match.id(), match.version(), changes, errors, notes, r));
        }
        return new Plan(out);
    }

    private static void diff(List<String> changes, String field, String was, String now) {
        if (now != null && !now.equals(nullToEmpty(was))) changes.add(field + ": " + (was == null || was.isEmpty() ? "none" : was) + " → " + now);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String normPath(String p) { return p.toLowerCase(Locale.ROOT).replaceAll("\\s*>\\s*", ">").trim(); }

    private static String resolveCategory(String cell, Map<String, String> byPath, Map<String, List<String>> byLeaf, List<String> errors) {
        String full = byPath.get(normPath(cell.replace('/', '>')));
        if (full != null) return full;
        List<String> leaf = byLeaf.get(cell.trim().toLowerCase(Locale.ROOT));
        if (leaf == null) { errors.add("The category '" + cell + "' does not exist. Create it first, or write its full path such as Hardware > Cement."); return null; }
        if (leaf.size() > 1) { errors.add("More than one category is called '" + cell + "'. Write its full path, for example " + leaf.get(0) + "."); return null; }
        return leaf.get(0);
    }

    // ── Writing a file ─────────────────────────────────────────────────────────

    /** One CSV line. Cells that start with = + @ or - (text) get an apostrophe so a spreadsheet does not run them as a formula. */
    public static String csvLine(List<String> cells, Set<Integer> numeric) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            String v = cells.get(i) == null ? "" : cells.get(i);
            if (!numeric.contains(i) && !v.isEmpty() && "=+@-".indexOf(v.charAt(0)) >= 0) v = "'" + v;
            if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0 || v.indexOf(';') >= 0)
                v = '"' + v.replace("\"", "\"\"") + '"';
            sb.append(v);
        }
        return sb.toString();
    }

    public static String template() {
        Set<Integer> numeric = Set.of(9, 10, 11);
        return String.join("\r\n", csvLine(COLUMNS, Set.of()),
                csvLine(List.of("CEM-50", "Cement 50kg", "General purpose cement", "Building > Cement", "each", "PPC", "6001234567890", "product", "active", "89.90", "62.00", "15", "no"), numeric)) + "\r\n";
    }
}
