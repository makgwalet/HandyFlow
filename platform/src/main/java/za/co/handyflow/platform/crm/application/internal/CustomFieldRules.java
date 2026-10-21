package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.CustomFieldType;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rules for tenant-defined custom fields: what a field definition may look like, how a field's key is made from its label,
 * and how values entered on a customer are checked and cleaned. Pure logic, no database.
 */
public final class CustomFieldRules {

    /** A field as the rules see it. Only active fields are passed to {@link #clean}. */
    public record Def(String key, String label, CustomFieldType type, boolean required, List<String> options) {}

    public static final int MAX_TEXT = 500;
    public static final int MAX_LABEL = 60;
    public static final int MAX_OPTIONS = 30;
    public static final int MAX_OPTION_LENGTH = 60;
    public static final int MAX_KEY = 40;
    public static final int MAX_FIELDS = 30;

    private CustomFieldRules() {}

    /** Why a definition cannot be saved, or null. */
    public static String definitionProblem(String label, CustomFieldType type, List<String> options) {
        if (label == null || label.isBlank()) return "Give the field a name";
        if (label.strip().length() > MAX_LABEL) return "The field name can be at most " + MAX_LABEL + " characters";
        if (type == null) return "Choose what kind of field this is";
        if (type == CustomFieldType.CHOICE) {
            List<String> clean = cleanOptions(options);
            if (clean.size() < 2) return "A choice field needs at least two options";
            if (clean.size() > MAX_OPTIONS) return "A choice field can have at most " + MAX_OPTIONS + " options";
            if (clean.stream().anyMatch(o -> o.length() > MAX_OPTION_LENGTH)) return "Each option can be at most " + MAX_OPTION_LENGTH + " characters";
        }
        return null;
    }

    /** Trimmed, blanks dropped, repeats (ignoring case) dropped, order kept. */
    public static List<String> cleanOptions(List<String> options) {
        if (options == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o == null || o.isBlank()) continue;
            String t = o.strip();
            if (seen.add(t.toLowerCase(Locale.ROOT))) out.add(t);
        }
        return out;
    }

    /** A short stable key from a label ("Fleet size" becomes "fleet_size"), made unique against the keys already used. */
    public static String keyFor(String label, Set<String> taken) {
        String base = Normalizer.normalize(label == null ? "" : label, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "field";
        if (base.length() > MAX_KEY - 3) base = base.substring(0, MAX_KEY - 3);
        String key = base;
        for (int i = 2; taken.contains(key); i++) key = base + "_" + i;
        return key;
    }

    /**
     * Checks the values for the active fields and returns what should be stored: the checked values for active fields,
     * plus whatever was already stored under fields that are no longer active (kept, not shown). A blank value clears a field.
     * Throws IllegalArgumentException naming the first problem.
     */
    public static Map<String, String> clean(List<Def> active, Map<String, String> input, Map<String, String> existing) {
        Map<String, Def> byKey = new LinkedHashMap<>();
        for (Def d : active) byKey.put(d.key(), d);
        Map<String, String> in = input == null ? Map.of() : input;
        for (String k : in.keySet()) {
            if (!byKey.containsKey(k)) throw new IllegalArgumentException("Unknown field '" + k + "'");
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (existing != null) existing.forEach((k, v) -> { if (!byKey.containsKey(k)) out.put(k, v); });
        for (Def d : active) {
            String raw = in.get(d.key());
            if (raw == null || raw.isBlank()) {
                if (d.required()) throw new IllegalArgumentException(d.label() + " is required");
                continue;
            }
            out.put(d.key(), checkValue(d, raw.strip()));
        }
        return out;
    }

    private static String checkValue(Def d, String v) {
        switch (d.type()) {
            case TEXT -> {
                if (v.length() > MAX_TEXT) throw new IllegalArgumentException(d.label() + " can be at most " + MAX_TEXT + " characters");
                return v;
            }
            case NUMBER -> {
                BigDecimal n;
                try {
                    n = new BigDecimal(v.replace(" ", ""));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(d.label() + " must be a number");
                }
                if (n.precision() - n.scale() > 15) throw new IllegalArgumentException(d.label() + " is too large");
                return n.stripTrailingZeros().toPlainString();
            }
            case DATE -> {
                try {
                    return LocalDate.parse(v).toString();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(d.label() + " must be a date like 2026-11-30");
                }
            }
            case CHOICE -> {
                for (String o : d.options()) if (o.equals(v)) return o;
                throw new IllegalArgumentException(d.label() + " must be one of: " + String.join(", ", d.options()));
            }
            case YES_NO -> {
                String l = v.toLowerCase(Locale.ROOT);
                if (l.equals("yes") || l.equals("true")) return "yes";
                if (l.equals("no") || l.equals("false")) return "no";
                throw new IllegalArgumentException(d.label() + " must be yes or no");
            }
            default -> throw new IllegalStateException("Unhandled field type " + d.type());
        }
    }
}
