package za.co.handyflow.platform.clinic.dto;

import java.util.List;

/** Allergies on record when a consultation was signed. {@code captured} is false for consultations signed before this existed. */
public record AllergySnapshotResponse(boolean captured, String capturedAt, List<Item> items) {

    public record Item(String allergen, String allergenType, String severity, String reaction) {}

    public static AllergySnapshotResponse notCaptured() { return new AllergySnapshotResponse(false, null, List.of()); }
}
