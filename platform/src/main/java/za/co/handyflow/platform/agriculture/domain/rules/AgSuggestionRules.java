package za.co.handyflow.platform.agriculture.domain.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** The farm's own breed and feed-type suggestions: what is already in use, tidied. Pure, so it can be tested without Spring. */
public final class AgSuggestionRules {

    private AgSuggestionRules() {}

    public static final int MAX_PER_LIST = 200;
    public static final int MAX_LENGTH = 100;

    /** Distinct values, trimmed, blanks and over-long values dropped, one spelling per case-insensitive value (the first seen), sorted, at most MAX_PER_LIST. */
    public static List<String> tidy(List<String> values) {
        Map<String, String> byKey = new LinkedHashMap<>();
        if (values != null) for (String v : values) {
            String t = v == null ? "" : v.trim();
            if (t.isEmpty() || t.length() > MAX_LENGTH) continue;
            byKey.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
        }
        List<String> out = new ArrayList<>(byKey.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out.size() > MAX_PER_LIST ? new ArrayList<>(out.subList(0, MAX_PER_LIST)) : out;
    }

    /** Rows of [speciesId, breed] (animals and groups together) as breeds per species. Rows with no species or breed are ignored. */
    public static Map<UUID, List<String>> breedsBySpecies(List<Object[]> rows) {
        Map<UUID, List<String>> raw = new TreeMap<>();
        if (rows != null) for (Object[] r : rows) {
            if (r == null || r.length < 2 || !(r[0] instanceof UUID species) || !(r[1] instanceof String breed)) continue;
            raw.computeIfAbsent(species, k -> new ArrayList<>()).add(breed);
        }
        Map<UUID, List<String>> out = new TreeMap<>();
        raw.forEach((k, v) -> { List<String> t = tidy(v); if (!t.isEmpty()) out.put(k, t); });
        return out;
    }
}
