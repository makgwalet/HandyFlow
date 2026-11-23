package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importing stock items from a spreadsheet (CSV). Every row is checked on the server. An item whose name is already on the farm
 * (ignoring case) is a DUPLICATE and is skipped, never changed. A row with a problem is an ERROR with the reason. One bad row
 * never stops the others. An opening quantity becomes a RECEIPT on the stock ledger, so the item's history starts with where its
 * stock came from. Pure and dependency-free so it can be tested without Spring or a database.
 */
public final class AgStockImportRules {

    private AgStockImportRules() {}

    public static final int MAX_ROWS = 500;
    public static final int MAX_NAME = 255;
    public static final int MAX_TEXT = 100;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    public static final String OK = "OK";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String ERROR = "ERROR";

    public static final List<String> CATEGORIES = List.of("FEED", "SEED", "FERTILISER", "CHEMICAL", "VETERINARY", "OTHER");

    /** The units the screens offer, spelled as they are stored. */
    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("kg", "kg"), Map.entry("kgs", "kg"), Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("g", "g"), Map.entry("gram", "g"), Map.entry("grams", "g"),
            Map.entry("t", "t"), Map.entry("ton", "t"), Map.entry("tons", "t"), Map.entry("tonne", "t"), Map.entry("tonnes", "t"),
            Map.entry("l", "L"), Map.entry("litre", "L"), Map.entry("litres", "L"), Map.entry("liter", "L"), Map.entry("liters", "L"),
            Map.entry("ml", "mL"), Map.entry("millilitre", "mL"), Map.entry("millilitres", "mL"),
            Map.entry("bag", "bags"), Map.entry("bags", "bags"), Map.entry("bale", "bales"), Map.entry("bales", "bales"),
            Map.entry("crate", "crates"), Map.entry("crates", "crates"), Map.entry("unit", "units"), Map.entry("units", "units"),
            Map.entry("dose", "doses"), Map.entry("doses", "doses"), Map.entry("head", "head"));

    /** One line of the file as typed. Everything is text so a bad value becomes a row error, not a failed request. */
    public record Row(String itemName, String category, String unit, String quantity, String unitCost, String reorderLevel,
                      String supplier, String packSizeKg) {}

    /** The item this row would create. {@code openingQuantity} is zero when none was given. */
    public record Parsed(String itemName, String category, String unit, BigDecimal openingQuantity, BigDecimal unitCost,
                         BigDecimal reorderLevel, String supplier, BigDecimal packSizeKg) {}

    public record Result(int line, String itemName, String status, String message, Parsed parsed) {}

    public static void requireRowCount(int rows) {
        if (rows == 0) throw new IllegalArgumentException("The file has no rows to import");
        if (rows > MAX_ROWS) throw new IllegalArgumentException("Import at most " + MAX_ROWS + " stock items at a time (the file has " + rows + ")");
    }

    /** Checks every row, in order. {@code existingNames} are the farm's current item names in lower case. */
    public static List<Result> plan(List<Row> rows, Set<String> existingNames) {
        requireRowCount(rows == null ? 0 : rows.size());
        Set<String> seen = new HashSet<>();
        List<Result> out = new ArrayList<>();
        int line = 0;
        for (Row r : rows) {
            line++;
            String name = clean(r.itemName());
            if (name.isEmpty()) { out.add(new Result(line, "", ERROR, "Item name is required", null)); continue; }
            if (name.length() > MAX_NAME) { out.add(new Result(line, name, ERROR, "Item name is longer than " + MAX_NAME + " characters", null)); continue; }
            String key = name.toLowerCase(Locale.ROOT);
            if (existingNames.contains(key)) { out.add(new Result(line, name, DUPLICATE, "'" + name + "' is already in this farm's stock, so it was skipped", null)); continue; }
            if (seen.contains(key)) { out.add(new Result(line, name, DUPLICATE, "'" + name + "' appears earlier in this file, so this row was skipped", null)); continue; }
            Result checked = check(line, name, r);
            if (OK.equals(checked.status())) seen.add(key);
            out.add(checked);
        }
        return out;
    }

    private static Result check(int line, String name, Row r) {
        String category = clean(r.category()).toUpperCase(Locale.ROOT);
        if (category.isEmpty()) return err(line, name, "Category is required (" + String.join(", ", CATEGORIES).toLowerCase(Locale.ROOT) + ")");
        if (!CATEGORIES.contains(category)) return err(line, name, "Category '" + clean(r.category()) + "' must be one of: " + String.join(", ", CATEGORIES).toLowerCase(Locale.ROOT));

        String unit = normalizeUnit(r.unit());
        if (clean(r.unit()).isEmpty()) return err(line, name, "Unit is required (for example kg, bags, L)");
        if (unit == null) return err(line, name, "Unit '" + clean(r.unit()) + "' is not one of: kg, g, t, L, mL, bags, bales, crates, units, doses, head");

        BigDecimal quantity = BigDecimal.ZERO, cost = null, reorder = null, pack = null;
        if (!clean(r.quantity()).isEmpty()) {
            quantity = parseNumber(r.quantity());
            if (quantity == null) return err(line, name, "Quantity '" + clean(r.quantity()) + "' is not a number");
            if (quantity.signum() < 0) return err(line, name, "Quantity cannot be negative");
            if (quantity.compareTo(MAX_AMOUNT) > 0) return err(line, name, "Quantity is not credible. Check the number");
        }
        if (!clean(r.unitCost()).isEmpty()) {
            cost = parseNumber(r.unitCost());
            if (cost == null) return err(line, name, "Unit cost '" + clean(r.unitCost()) + "' is not a number");
            if (cost.signum() < 0) return err(line, name, "Unit cost cannot be negative");
            if (cost.compareTo(MAX_AMOUNT) > 0) return err(line, name, "Unit cost is not credible. Check the number");
            cost = cost.setScale(2, RoundingMode.HALF_UP);
        }
        if (!clean(r.reorderLevel()).isEmpty()) {
            reorder = parseNumber(r.reorderLevel());
            if (reorder == null) return err(line, name, "Reorder level '" + clean(r.reorderLevel()) + "' is not a number");
            if (reorder.signum() < 0) return err(line, name, "Reorder level cannot be negative");
            if (reorder.compareTo(MAX_AMOUNT) > 0) return err(line, name, "Reorder level is not credible. Check the number");
        }
        if (!clean(r.packSizeKg()).isEmpty()) {
            BigDecimal p = parseNumber(r.packSizeKg());
            if (p == null) return err(line, name, "Kg per bag '" + clean(r.packSizeKg()) + "' is not a number");
            try { pack = AgPackRules.requirePackSize(unit, p); }
            catch (IllegalArgumentException e) { return err(line, name, e.getMessage()); }
        }
        String supplier = clean(r.supplier());
        if (supplier.length() > MAX_TEXT) return err(line, name, "Supplier is longer than " + MAX_TEXT + " characters");
        return new Result(line, name, OK, null, new Parsed(name, category, unit, quantity.setScale(3, RoundingMode.HALF_UP), cost, reorder,
                supplier.isEmpty() ? null : supplier, pack));
    }

    private static Result err(int line, String name, String message) { return new Result(line, name, ERROR, message, null); }

    private static String clean(String s) { return s == null ? "" : s.trim(); }

    /** The unit as the screens store it (kg, t, L, bags...), from what was typed in any case and the usual spellings; null if unknown. */
    public static String normalizeUnit(String raw) {
        return UNITS.get(clean(raw).toLowerCase(Locale.ROOT));
    }

    /** A number typed with a dot or a comma as the decimal mark ("12,5"), spaces allowed as thousands separators; null if it is not one. */
    public static BigDecimal parseNumber(String raw) {
        String s = clean(raw).replace(" ", "").replace("\u00A0", "");
        if (s.isEmpty()) return null;
        if (s.contains(",") && s.contains(".")) s = s.replace(",", "");
        else if (s.indexOf(',') >= 0 && s.indexOf(',') == s.lastIndexOf(',')) s = s.replace(',', '.');
        else if (s.contains(",")) return null;
        if (!s.matches("-?\\d+(\\.\\d+)?")) return null;
        return new BigDecimal(s);
    }

    public static int count(List<Result> results, String status) {
        int n = 0;
        for (Result r : results) if (status.equals(r.status())) n++;
        return n;
    }
}
