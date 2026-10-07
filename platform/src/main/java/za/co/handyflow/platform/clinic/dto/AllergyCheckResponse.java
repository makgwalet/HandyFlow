package za.co.handyflow.platform.clinic.dto;

import java.util.List;

/** Result of comparing a medicine name with a patient's recorded allergies. {@code note} is always shown to the prescriber. */
public record AllergyCheckResponse(List<Alert> alerts, String note) {

    public record Alert(String allergen, String severity, String reaction) {}

    public static final String NOTE = "Compared by name only against recorded allergies. Drug classes, brand names and "
            + "cross-reactivity are not checked, so no match does not mean the medicine is safe for this patient.";

    public static AllergyCheckResponse of(List<Alert> alerts) { return new AllergyCheckResponse(alerts, NOTE); }
}
