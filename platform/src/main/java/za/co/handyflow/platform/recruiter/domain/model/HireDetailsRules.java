package za.co.handyflow.platform.recruiter.domain.model;

import java.util.Set;

/**
 * The personal details a recruiter may enter when a hire becomes an employee. HR checks the date of birth, phone and the rest again when the
 * employee is created; this refuses what HR would store without question: a gender outside the list HR offers, and half an emergency contact.
 */
public final class HireDetailsRules {

    public static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "NON_BINARY", "PREFER_NOT_TO_SAY");

    private HireDetailsRules() {}

    public static String problem(String gender, String contactName, String contactPhone) {
        if (gender != null && !gender.isBlank() && !GENDERS.contains(gender.trim()))
            return "Gender must be one of: male, female, non-binary, or prefer not to say.";
        boolean name = contactName != null && !contactName.isBlank();
        boolean phone = contactPhone != null && !contactPhone.isBlank();
        if (name != phone) return "Give the emergency contact's name and phone number together, or neither.";
        if (name && contactName.trim().length() > 120) return "The emergency contact's name is too long.";
        return null;
    }
}
