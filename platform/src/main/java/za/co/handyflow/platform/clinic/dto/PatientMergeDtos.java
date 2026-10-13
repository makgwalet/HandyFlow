package za.co.handyflow.platform.clinic.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Merging two records of the same patient (patch 0188). */
public final class PatientMergeDtos {
    private PatientMergeDtos() {}

    public record Card(UUID id, String patientNumber, String fullName, LocalDate dateOfBirth, String idNumber) {}

    public record Count(String table, String label, int rows) {}

    public record ConflictView(String field, String text, boolean blocking) {}

    /** What would happen. {@code blockers} non-empty means it cannot be done now. */
    public record Preview(Card keep, Card mergeAway, List<Count> moves, List<String> fills, List<ConflictView> conflicts,
                          List<String> blockers, List<String> staysBehind, boolean needsAcknowledgement) {}

    public record MergeRequest(UUID survivorId, UUID mergedId, String reason, boolean acknowledgeConflicts) {}

    public record MergeResult(UUID mergeId, UUID survivorId, UUID mergedId, int rowsMoved, List<Count> moves, List<String> fills) {}
}
