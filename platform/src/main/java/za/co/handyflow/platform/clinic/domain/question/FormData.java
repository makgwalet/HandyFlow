package za.co.handyflow.platform.clinic.domain.question;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/** Helpers for the answers stored on a consultation: {"groups": {code: {"version": n, "answers": {...}}}}. */
public final class FormData {

    private FormData() {}

    /** Returns a copy of {@code existing} with one group's answers replaced. Other groups are untouched. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> withGroup(Map<String, Object> existing, String groupCode, int version,
                                                Map<String, Object> answers) {
        Map<String, Object> root = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        Map<String, Object> groups = root.get("groups") instanceof Map<?, ?> g
                ? new LinkedHashMap<>((Map<String, Object>) g) : new LinkedHashMap<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("version", version);
        entry.put("answers", new LinkedHashMap<>(answers));
        groups.put(groupCode, entry);
        root.put("groups", groups);
        return root;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> answersOf(Map<String, Object> formData, String groupCode) {
        if (formData == null || !(formData.get("groups") instanceof Map<?, ?> g)) return Map.of();
        Object entry = ((Map<String, Object>) g).get(groupCode);
        if (entry instanceof Map<?, ?> e && e.get("answers") instanceof Map<?, ?> a) return (Map<String, Object>) a;
        return Map.of();
    }

    /** Whole months between birth and today; null when the date of birth is unknown. */
    public static Integer ageMonths(LocalDate dob, LocalDate today) {
        if (dob == null || dob.isAfter(today)) return null;
        Period p = Period.between(dob, today);
        return p.getYears() * 12 + p.getMonths();
    }
}
