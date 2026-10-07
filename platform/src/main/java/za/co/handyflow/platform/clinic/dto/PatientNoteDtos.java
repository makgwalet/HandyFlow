package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for sticky notes and alerts on a patient's file. */
public final class PatientNoteDtos {
    private PatientNoteDtos() {}

    /** kind is NOTE or ALERT; severity (INFO, WARNING, CRITICAL) applies to alerts only and defaults to WARNING. */
    public record CreateNoteRequest(String kind, String severity, String body) {}

    public record NoteResponse(UUID id, String kind, String severity, String body,
                               UUID createdBy, Instant createdAt, Instant resolvedAt, UUID resolvedBy) {}
}
