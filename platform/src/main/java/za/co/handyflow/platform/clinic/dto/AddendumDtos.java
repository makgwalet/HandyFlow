package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public final class AddendumDtos {
    private AddendumDtos() {}

    /** {@code kind} may be left out (a plain note); see AddendumRules for the choices. */
    public record AddAddendumRequest(String text, String kind) {
        public AddAddendumRequest(String text) { this(text, null); }
    }

    public record AddendumResponse(UUID id, UUID consultationId, UUID authorUserId, String text, Instant createdAt, String kind, String kindLabel) {
        public AddendumResponse(UUID id, UUID consultationId, UUID authorUserId, String text, Instant createdAt) { this(id, consultationId, authorUserId, text, createdAt, "OTHER", "Note"); }
    }
}
