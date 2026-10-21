package za.co.handyflow.platform.catalogue.domain.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure rules for item variants (Size, Colour, ...): option and value cleaning, the matrix of combinations, the key that
 * makes a combination unique, and the generated name and SKU. No Spring, no database.
 * A variant is itself a catalogue item (own id, SKU, barcode, price), so quotes, invoices and POS sell it unchanged.
 */
public final class VariantRules {
    private VariantRules() {}

    public static final int MAX_OPTIONS = 3, MAX_VALUES = 20, MAX_OPTION_NAME = 30, MAX_VALUE = 50, MAX_PER_REQUEST = 100, MAX_PER_PARENT = 200;

    public static String optionName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Each option needs a name, for example Size or Colour.");
        if (s.length() > MAX_OPTION_NAME) throw new IllegalArgumentException("An option name can be at most " + MAX_OPTION_NAME + " characters.");
        return s;
    }

    public static String optionValue(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("A value cannot be empty.");
        if (s.length() > MAX_VALUE) throw new IllegalArgumentException("A value can be at most " + MAX_VALUE + " characters.");
        if (s.contains("|") || s.contains("=")) throw new IllegalArgumentException("A value cannot contain | or =.");
        return s;
    }

    /** Cleans the values of one option: 1 to 20, no repeats ignoring case. */
    public static List<String> values(String option, List<String> raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("The option '" + option + "' needs at least one value.");
        if (raw.size() > MAX_VALUES) throw new IllegalArgumentException("The option '" + option + "' can have at most " + MAX_VALUES + " values.");
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String r : raw) {
            String v = optionValue(r);
            if (!seen.add(v.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("The value '" + v + "' appears twice in '" + option + "'.");
            out.add(v);
        }
        return out;
    }

    /** Cleans the option names: 1 to 3, no repeats ignoring case. */
    public static List<String> optionNames(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("Add at least one option, for example Size.");
        if (raw.size() > MAX_OPTIONS) throw new IllegalArgumentException("An item can have at most " + MAX_OPTIONS + " options.");
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String r : raw) {
            String n = optionName(r);
            if (!seen.add(n.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("The option '" + n + "' appears twice.");
            out.add(n);
        }
        return out;
    }

    /** Every combination of one value per option, in option order (the first option changes slowest). */
    public static List<List<String>> combinations(List<List<String>> valuesPerOption) {
        long total = 1;
        for (var v : valuesPerOption) { total *= Math.max(1, v.size()); if (total > MAX_PER_REQUEST) throw new IllegalArgumentException("That makes more than " + MAX_PER_REQUEST + " variants. Use fewer values."); }
        List<List<String>> out = new ArrayList<>();
        out.add(new ArrayList<>());
        for (var values : valuesPerOption) {
            List<List<String>> next = new ArrayList<>();
            for (var prefix : out) for (String v : values) { var c = new ArrayList<>(prefix); c.add(v); next.add(c); }
            out = next;
        }
        return out;
    }

    /** "size=large|colour=red": lower case, so Large and large are the same combination. */
    public static String key(List<String> optionNames, List<String> values) {
        if (optionNames.size() != values.size()) throw new IllegalArgumentException("Choose one value for each option.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(optionNames.get(i).trim().toLowerCase(Locale.ROOT)).append('=').append(values.get(i).trim().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** "Large / Red". */
    public static String label(List<String> values) { return String.join(" / ", values); }

    /** "Cement 50kg — Large / Red". */
    public static String variantName(String parentName, List<String> values) {
        String n = parentName + " — " + label(values);
        if (n.length() > CatalogueRules.MAX_NAME) throw new IllegalArgumentException("The variant name would be longer than " + CatalogueRules.MAX_NAME + " characters. Shorten the item name or the values.");
        return n;
    }

    /** Parent SKU plus three letters of each value: CEM-50 + Large + Red = CEM-50-LAR-RED. Null when the parent has no SKU. */
    public static String suggestSku(String parentSku, List<String> values) {
        if (parentSku == null || parentSku.isBlank()) return null;
        StringBuilder sb = new StringBuilder(parentSku.trim().toUpperCase(Locale.ROOT));
        for (String v : values) {
            String part = v.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (part.isEmpty()) part = "X";
            sb.append('-').append(part, 0, Math.min(3, part.length()));
        }
        return sb.length() > CatalogueRules.MAX_SKU ? sb.substring(0, CatalogueRules.MAX_SKU) : sb.toString();
    }
}
