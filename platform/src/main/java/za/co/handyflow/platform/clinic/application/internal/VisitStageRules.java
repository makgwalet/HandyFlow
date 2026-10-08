package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stage;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Which consultation stages a visit type requires (CLINIC-DEC-012). Pure, so it is tested without a database.
 * Visit types nobody has configured require Symptoms and Diagnosis (CLINIC-DEC-010), never less.
 */
public final class VisitStageRules {
    private VisitStageRules() {}

    public static final String SYMPTOMS = "SYMPTOMS", EXAMINATION = "EXAMINATION", DIAGNOSIS = "DIAGNOSIS", PLAN = "PLAN";
    /** Clinical order. */
    public static final List<String> ORDER = List.of(SYMPTOMS, EXAMINATION, DIAGNOSIS, PLAN);
    public static final Set<String> DEFAULT_REQUIRED = Set.of(SYMPTOMS, DIAGNOSIS);
    public static final String DEFAULT_VISIT_TYPE = "CONSULTATION";
    private static final Pattern VISIT_TYPE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,49}$");

    /** Upper-cased visit type; a missing one means a general consultation. */
    public static String visitType(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_VISIT_TYPE;
        String v = raw.trim().toUpperCase();
        if (!VISIT_TYPE.matcher(v).matches()) throw new IllegalArgumentException("That is not a valid visit type.");
        return v;
    }

    /** All four stages exactly once, in clinical order. Throws with a plain message on anything else. */
    public static List<Stage> check(List<Stage> requested) {
        if (requested == null) throw new IllegalArgumentException("Send the stages and whether each is required.");
        Map<String, Boolean> byStage = new LinkedHashMap<>();
        for (Stage s : requested) {
            String name = s == null || s.stage() == null ? "" : s.stage().trim().toUpperCase();
            if (!ORDER.contains(name)) throw new IllegalArgumentException("'" + name + "' is not a consultation stage.");
            if (byStage.put(name, s.required()) != null) throw new IllegalArgumentException("Stage " + name + " is listed twice.");
        }
        if (byStage.size() != ORDER.size()) throw new IllegalArgumentException("Give all four stages: Symptoms, Examination, Diagnosis and Plan.");
        return ORDER.stream().map(n -> new Stage(n, byStage.get(n))).toList();
    }

    /** The required stage names; falls back to the default when no stages are configured. */
    public static Set<String> required(List<Stage> stages) {
        if (stages == null || stages.isEmpty()) return DEFAULT_REQUIRED;
        Set<String> out = new LinkedHashSet<>();
        for (String n : ORDER) {
            if (stages.stream().anyMatch(s -> n.equals(s.stage()) && s.required())) out.add(n);
        }
        return out;
    }
}
