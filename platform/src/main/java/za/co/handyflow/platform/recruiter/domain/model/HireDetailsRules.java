package za.co.handyflow.platform.recruiter.domain.model;

import java.util.Set;

/**
 * The personal details a recruiter may enter when a hire becomes an employee. HR checks the date of birth, phone and the rest again when the
 * employee is created; this refuses what HR would store without question: a gender outside the list HR offers, and half an emergency contact.
 */
public final class HireDetailsRules {

    public static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "NON_BINARY", "PREFER_NOT_TO_SAY");

    private HireDetailsRules() {}

    public static final Set<String> RACES = Set.of("AFRICAN", "COLOURED", "INDIAN", "WHITE", "OTHER");
    public static final int MAX_RELATION = 50;

    public static String problem(String gender, String contactName, String contactPhone) {
        return problem(gender, null, contactName, contactPhone, null);
    }

    /** Null when race, relation and the rest can go to HR. Race and relation are optional; a relation needs a contact name. */
    public static String problem(String gender, String race, String contactName, String contactPhone, String relation) {
        if (race != null && !race.isBlank() && !RACES.contains(race.trim().toUpperCase(java.util.Locale.ROOT)))
            return "Race must be one of: African, Coloured, Indian, White or Other.";
        if (relation != null && !relation.isBlank()) {
            if (contactName == null || contactName.isBlank()) return "Give the emergency contact's name as well as their relation.";
            if (relation.trim().length() > MAX_RELATION) return "The emergency contact's relation is too long (" + MAX_RELATION + " characters at most).";
        }
        if (gender != null && !gender.isBlank() && !GENDERS.contains(gender.trim()))
            return "Gender must be one of: male, female, non-binary, or prefer not to say.";
        boolean name = contactName != null && !contactName.isBlank();
        boolean phone = contactPhone != null && !contactPhone.isBlank();
        if (name != phone) return "Give the emergency contact's name and phone number together, or neither.";
        if (name && contactName.trim().length() > 120) return "The emergency contact's name is too long.";
        return null;
    }
}
