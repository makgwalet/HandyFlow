package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The catalogue's pure rules: how identifiers are cleaned and checked, which status moves are allowed and
 * how a change is described for the audit trail. No Spring, no database, so it is unit-testable on its own.
 * Every method that rejects input throws IllegalArgumentException with a message fit to show the user.
 */
public final class CatalogueRules {
    private CatalogueRules() {}

    public static final int MAX_NAME = 255, MAX_SKU = 50, MAX_BARCODE = 100, MAX_BRAND = 100, MAX_REASON = 255;
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9._/-]*$");
    private static final Pattern BARCODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999.99");

    /** Blank becomes null; otherwise trimmed and upper-cased so "ab-1" and "AB-1" are the same SKU. */
    public static String normalizeSku(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.length() > MAX_SKU) throw new IllegalArgumentException("The SKU can be at most " + MAX_SKU + " characters.");
        if (!SKU.matcher(s).matches()) throw new IllegalArgumentException("The SKU can only use letters, numbers and . _ / - (no spaces).");
        return s;
    }

    /** Blank becomes null; otherwise trimmed. Letters are kept as typed. */
    public static String normalizeBarcode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.length() > MAX_BARCODE) throw new IllegalArgumentException("The barcode can be at most " + MAX_BARCODE + " characters.");
        if (!BARCODE.matcher(s).matches()) throw new IllegalArgumentException("The barcode can only use letters, numbers and . _ / - (no spaces).");
        return s;
    }

    public static String normalizeName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Item name is required.");
        if (s.length() > MAX_NAME) throw new IllegalArgumentException("The name can be at most " + MAX_NAME + " characters.");
        return s;
    }

    public static String normalizeBrand(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.length() > MAX_BRAND) throw new IllegalArgumentException("The brand can be at most " + MAX_BRAND + " characters.");
        return s;
    }

    /** A price or cost: not negative, not absurd, at most 2 decimals. */
    public static BigDecimal checkMoney(String what, BigDecimal v) {
        if (v == null) return null;
        if (v.signum() < 0) throw new IllegalArgumentException(what + " cannot be negative.");
        if (v.compareTo(MAX_MONEY) > 0) throw new IllegalArgumentException(what + " is too large.");
        if (v.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException(what + " can have at most 2 decimals.");
        return v.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    /** VAT-exempt items always carry a 0% rate, which is how POS and invoicing already read "exempt". */
    public static BigDecimal effectiveVat(boolean vatExempt, BigDecimal rate) {
        return vatExempt ? new BigDecimal("0.00") : rate;
    }

    /** The reason, trimmed, or an error when the target state needs one and it is missing. */
    public static String checkTransition(CatalogueItemStatus from, CatalogueItemStatus to, String reason) {
        Objects.requireNonNull(from); Objects.requireNonNull(to);
        if (from == to) throw new IllegalArgumentException("The item is already " + label(to) + ".");
        if (!from.canMoveTo(to)) throw new IllegalArgumentException("A " + label(from) + " item cannot become " + label(to) + ".");
        String r = reason == null ? "" : reason.trim();
        if (to.needsReason() && r.length() < 3) throw new IllegalArgumentException("Say why (at least 3 characters).");
        if (r.length() > MAX_REASON) throw new IllegalArgumentException("The reason can be at most " + MAX_REASON + " characters.");
        return r.isEmpty() ? null : r;
    }

    public static String label(CatalogueItemStatus s) { return s.name().toLowerCase(Locale.ROOT); }

    /** Human description of what changed, "" when nothing did. Used for the audit trail. */
    public static String describeChanges(Snapshot before, Snapshot after) {
        List<String> out = new ArrayList<>();
        change(out, "name", before.name, after.name);
        change(out, "description", before.description, after.description);
        change(out, "SKU", before.sku, after.sku);
        change(out, "barcode", before.barcode, after.barcode);
        change(out, "brand", before.brand, after.brand);
        change(out, "type", before.type, after.type);
        change(out, "unit", before.unit, after.unit);
        change(out, "category", before.category, after.category);
        changeMoney(out, "price", before.price, after.price);
        changeMoney(out, "cost", before.cost, after.cost);
        changeMoney(out, "VAT %", before.vat, after.vat);
        if (before.vatExempt != after.vatExempt) out.add("VAT exempt: " + (before.vatExempt ? "yes" : "no") + " -> " + (after.vatExempt ? "yes" : "no"));
        String joined = String.join("; ", out);
        return joined.length() > 1000 ? joined.substring(0, 997) + "..." : joined;
    }

    public static boolean priceChanged(Snapshot before, Snapshot after) {
        return differs(before.price, after.price) || differs(before.cost, after.cost) || differs(before.vat, after.vat);
    }

    private static void change(List<String> out, String field, Object a, Object b) {
        if (!Objects.equals(a, b)) out.add(field + ": " + show(a) + " -> " + show(b));
    }
    private static void changeMoney(List<String> out, String field, BigDecimal a, BigDecimal b) {
        if (differs(a, b)) out.add(field + ": " + show(a) + " -> " + show(b));
    }
    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a != b;
        return a.compareTo(b) != 0;
    }
    private static String show(Object o) {
        if (o == null) return "(none)";
        String s = o instanceof BigDecimal d ? d.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : o.toString();
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    /** The fields that matter for change tracking. */
    public record Snapshot(String name, String description, String sku, String barcode, String brand, String type, String unit,
                           String category, BigDecimal price, BigDecimal cost, BigDecimal vat, boolean vatExempt) {}
}
