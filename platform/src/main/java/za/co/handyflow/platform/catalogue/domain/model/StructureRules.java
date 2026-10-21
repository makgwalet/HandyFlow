package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Pure rules for units of measure, the category tree and attribute definitions/values.
 * No Spring, no database. Every rejection is an IllegalArgumentException with a message fit to show the user.
 */
public final class StructureRules {
    private StructureRules() {}

    public static final int MAX_UNIT_CODE = 50, MAX_UNIT_NAME = 60, MAX_DECIMALS = 4;
    public static final int MAX_DEPTH = 5;
    public static final int MAX_ATTR_CODE = 40, MAX_ATTR_LABEL = 60, MAX_OPTIONS = 50, MAX_OPTION_LEN = 60, MAX_VALUE_LEN = 500;
    private static final Pattern ATTR_CODE = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** What the rules need to know about an attribute definition. */
    public record AttrSpec(UUID id, String code, String label, AttributeType type, boolean required,
                           List<String> options, BigDecimal min, BigDecimal max, Integer maxLength) {}

    // ── Units ─────────────────────────────────────────────────────────────────

    /** A unit code is what shows on quotes and invoices ("m²", "each"): trimmed, no inner line breaks. */
    public static String unitCode(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("The unit code is required.");
        if (s.length() > MAX_UNIT_CODE) throw new IllegalArgumentException("The unit code can be at most " + MAX_UNIT_CODE + " characters.");
        if (s.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("The unit code cannot contain line breaks or control characters.");
        return s;
    }

    public static String unitName(String raw, String code) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return code;
        if (s.length() > MAX_UNIT_NAME) throw new IllegalArgumentException("The unit name can be at most " + MAX_UNIT_NAME + " characters.");
        return s;
    }

    /** How many decimals a quantity in this unit may have (0 = whole numbers only). */
    public static int unitDecimals(Integer raw) {
        int d = raw == null ? 2 : raw;
        if (d < 0 || d > MAX_DECIMALS) throw new IllegalArgumentException("Decimals must be between 0 and " + MAX_DECIMALS + ".");
        return d;
    }

    // ── Category tree ─────────────────────────────────────────────────────────

    /** True when making {@code newParent} the parent of {@code category} would create a loop (newParent is inside category's subtree). */
    public static boolean wouldCycle(UUID category, UUID newParent, Function<UUID, UUID> parentOf) {
        UUID cur = newParent;
        int guard = 0;
        while (cur != null && guard++ < 1000) {
            if (cur.equals(category)) return true;
            cur = parentOf.apply(cur);
        }
        return false;
    }

    /** Levels from the root down to and including this category (a root category has depth 1). */
    public static int depthOf(UUID category, Function<UUID, UUID> parentOf) {
        int d = 0;
        UUID cur = category;
        while (cur != null && d < 1000) { d++; cur = parentOf.apply(cur); }
        return d;
    }

    /** Check a placement: no loop and the deepest descendant stays within {@link #MAX_DEPTH}. */
    public static void checkPlacement(UUID category, UUID newParent, int subtreeHeight, Function<UUID, UUID> parentOf) {
        if (newParent == null) return;
        if (category != null && wouldCycle(category, newParent, parentOf))
            throw new IllegalArgumentException("A category cannot be moved inside itself or one of its own sub-categories.");
        int parentDepth = depthOf(newParent, parentOf);
        if (parentDepth + Math.max(1, subtreeHeight) > MAX_DEPTH)
            throw new IllegalArgumentException("Categories can be at most " + MAX_DEPTH + " levels deep.");
    }

    /** "Building › Cement › Bagged" for display. Stops at a missing parent. */
    public static String pathOf(UUID category, Function<UUID, UUID> parentOf, Function<UUID, String> nameOf) {
        List<String> parts = new ArrayList<>();
        UUID cur = category;
        int guard = 0;
        while (cur != null && guard++ < 1000) {
            String n = nameOf.apply(cur);
            if (n == null) break;
            parts.add(0, n);
            cur = parentOf.apply(cur);
        }
        return String.join(" › ", parts);
    }

    /** The category and everything below it. */
    public static List<UUID> subtree(UUID root, Map<UUID, UUID> parentById) {
        List<UUID> out = new ArrayList<>(List.of(root));
        for (int i = 0; i < out.size() && out.size() < 10000; i++) {
            UUID p = out.get(i);
            parentById.forEach((id, parent) -> { if (p.equals(parent) && !out.contains(id)) out.add(id); });
        }
        return out;
    }

    /** The category and its ancestors, nearest first. */
    public static List<UUID> selfAndAncestors(UUID category, Function<UUID, UUID> parentOf) {
        List<UUID> out = new ArrayList<>();
        UUID cur = category;
        while (cur != null && out.size() < 1000 && !out.contains(cur)) { out.add(cur); cur = parentOf.apply(cur); }
        return out;
    }

    // ── Attribute definitions ─────────────────────────────────────────────────

    public static String attributeCode(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) throw new IllegalArgumentException("The attribute code is required.");
        if (s.length() > MAX_ATTR_CODE) throw new IllegalArgumentException("The attribute code can be at most " + MAX_ATTR_CODE + " characters.");
        if (!ATTR_CODE.matcher(s).matches()) throw new IllegalArgumentException("The attribute code must start with a letter and use only letters, numbers and underscores.");
        return s;
    }

    public static String attributeLabel(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("The attribute name is required.");
        if (s.length() > MAX_ATTR_LABEL) throw new IllegalArgumentException("The attribute name can be at most " + MAX_ATTR_LABEL + " characters.");
        return s;
    }

    /** Options for a LIST attribute: trimmed, blanks dropped, duplicates (ignoring case) rejected. Other types must have none. */
    public static List<String> attributeOptions(AttributeType type, List<String> raw) {
        List<String> in = raw == null ? List.of() : raw;
        if (type != AttributeType.LIST) {
            if (in.stream().anyMatch(s -> s != null && !s.isBlank())) throw new IllegalArgumentException("Only a list attribute can have options.");
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String o : in) {
            String s = o == null ? "" : o.trim();
            if (s.isEmpty()) continue;
            if (s.length() > MAX_OPTION_LEN) throw new IllegalArgumentException("An option can be at most " + MAX_OPTION_LEN + " characters.");
            if (s.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("An option cannot contain line breaks.");
            if (!seen.add(s.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("The option '" + s + "' is listed twice.");
            out.add(s);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("A list attribute needs at least one option.");
        if (out.size() > MAX_OPTIONS) throw new IllegalArgumentException("A list attribute can have at most " + MAX_OPTIONS + " options.");
        return out;
    }

    /** Range and length settings must suit the type. Returns nothing; throws when inconsistent. */
    public static void checkLimits(AttributeType type, BigDecimal min, BigDecimal max, Integer maxLength) {
        if (type != AttributeType.NUMBER && (min != null || max != null)) throw new IllegalArgumentException("Minimum and maximum apply to number attributes only.");
        if (type != AttributeType.TEXT && maxLength != null) throw new IllegalArgumentException("A maximum length applies to text attributes only.");
        if (min != null && max != null && min.compareTo(max) > 0) throw new IllegalArgumentException("The minimum cannot be above the maximum.");
        if (maxLength != null && (maxLength < 1 || maxLength > MAX_VALUE_LEN)) throw new IllegalArgumentException("The maximum length must be between 1 and " + MAX_VALUE_LEN + ".");
    }

    // ── Attribute values ──────────────────────────────────────────────────────

    /**
     * Check one value against its definition and return the form to store, or null when the value is empty
     * (an empty value means "not set"; whether that is allowed is decided by {@link #checkRequired}).
     */
    public static String normalizeValue(AttrSpec a, String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return null;
        String who = a.label() + ": ";
        switch (a.type()) {
            case TEXT -> {
                int limit = a.maxLength() != null ? a.maxLength() : MAX_VALUE_LEN;
                if (s.length() > limit) throw new IllegalArgumentException(who + "at most " + limit + " characters.");
                return s;
            }
            case NUMBER -> {
                BigDecimal n;
                try { n = new BigDecimal(s.replace(",", ".")); } catch (NumberFormatException e) { throw new IllegalArgumentException(who + "'" + s + "' is not a number."); }
                if (a.min() != null && n.compareTo(a.min()) < 0) throw new IllegalArgumentException(who + "must be at least " + a.min().stripTrailingZeros().toPlainString() + ".");
                if (a.max() != null && n.compareTo(a.max()) > 0) throw new IllegalArgumentException(who + "must be at most " + a.max().stripTrailingZeros().toPlainString() + ".");
                return n.stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> {
                String l = s.toLowerCase(Locale.ROOT);
                if (l.equals("true") || l.equals("yes")) return "true";
                if (l.equals("false") || l.equals("no")) return "false";
                throw new IllegalArgumentException(who + "choose yes or no.");
            }
            case DATE -> {
                try { return LocalDate.parse(s).toString(); } catch (DateTimeParseException e) { throw new IllegalArgumentException(who + "use the date format YYYY-MM-DD."); }
            }
            case LIST -> {
                for (String o : a.options()) if (o.equalsIgnoreCase(s)) return o;
                throw new IllegalArgumentException(who + "'" + s + "' is not one of the allowed options.");
            }
        }
        throw new IllegalStateException("Unknown attribute type " + a.type());
    }

    /**
     * Check every supplied value against the attributes that apply. Returns the cleaned values keyed by attribute id
     * (empty values left out). Unknown ids are rejected; the first problem found is reported.
     */
    public static Map<UUID, String> checkValues(List<AttrSpec> applicable, Map<UUID, String> supplied) {
        Map<UUID, AttrSpec> byId = new java.util.LinkedHashMap<>();
        applicable.forEach(a -> byId.put(a.id(), a));
        Map<UUID, String> out = new java.util.LinkedHashMap<>();
        if (supplied == null) return out;
        for (var e : supplied.entrySet()) {
            AttrSpec a = byId.get(e.getKey());
            if (a == null) throw new IllegalArgumentException("An attribute was sent that does not apply to this item's category.");
            String v = normalizeValue(a, e.getValue());
            if (v != null) out.put(a.id(), v);
        }
        return out;
    }

    /** Names of required attributes that have no value. Draft items may be incomplete, so callers skip this for drafts. */
    public static List<String> missingRequired(List<AttrSpec> applicable, Map<UUID, String> values) {
        List<String> out = new ArrayList<>();
        for (AttrSpec a : applicable) if (a.required() && (values == null || !values.containsKey(a.id()))) out.add(a.label());
        return out;
    }

    public static void checkRequired(List<AttrSpec> applicable, Map<UUID, String> values) {
        List<String> missing = missingRequired(applicable, values);
        if (!missing.isEmpty()) throw new IllegalArgumentException("Fill in the required attribute" + (missing.size() == 1 ? "" : "s") + ": " + String.join(", ", missing) + ".");
    }
}
