package za.co.handyflow.platform.clinic.application.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares a medicine's NAME with a patient's recorded allergies. Pure: no database, no Spring.
 * <p>
 * This is a name check only. It does not know drug classes, brand names, ingredients or cross-reactivity (a recorded
 * penicillin allergy is not matched to "amoxicillin"), so a clear result is never evidence that a medicine is safe.
 * Reference data for that is a separate, licensed concern (DEC-CLINIC-003). Food and environmental allergies are
 * skipped. A hit is a prompt for the clinician, who decides; nothing here blocks a prescription for good.
 */
final class AllergyMatcher {

    private AllergyMatcher() {}

    record Fact(String allergen, String allergenType, String severity, String reaction) {}

    record Alert(String allergen, String severity, String reaction) {
        String summary() {
            return allergen + (severity == null || severity.isBlank() ? "" : " (" + severity.toLowerCase(Locale.ROOT) + ")");
        }
    }

    static List<Alert> match(String medicineName, List<Fact> allergies) {
        List<Alert> out = new ArrayList<>();
        String med = normalise(medicineName);
        if (med.isEmpty() || allergies == null) return out;
        String firstWord = med.contains(" ") ? med.substring(0, med.indexOf(' ')) : med;
        for (Fact f : allergies) {
            if (f == null || f.allergen() == null) continue;
            String type = f.allergenType() == null ? "UNKNOWN" : f.allergenType();
            if (type.equals("FOOD") || type.equals("ENVIRONMENT")) continue;
            String allergen = normalise(f.allergen());
            if (allergen.length() < 4) continue;
            boolean hit = med.contains(allergen) || (firstWord.length() >= 5 && allergen.contains(firstWord));
            if (hit) out.add(new Alert(f.allergen().trim(), f.severity(), f.reaction()));
        }
        return out;
    }

    static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
