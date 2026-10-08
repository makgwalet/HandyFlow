package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

/** Restricted patient records and break-glass access. */
public final class RestrictedRecordDtos {
    private RestrictedRecordDtos() {}

    public record FlagRequest(String category, String reason) {}
    public record ReasonRequest(String reason) {}
    public record AcknowledgeRequest(String note) {}
    public record PrintRequest(String document) {}

    /** canAccess: this user can open the clinical record now (not restricted, standing access, or an active break-glass session). */
    public record Status(boolean restricted, String category, Instant flaggedAt, boolean canAccess, Instant breakGlassUntil) {}

    public record Session(UUID sessionId, Instant startedAt, Instant expiresAt) {}

    public record SessionRow(UUID id, UUID patientId, String patientName, UUID userId, String userName, String reason,
                             Instant startedAt, Instant expiresAt, int itemsViewed, int documents,
                             Instant acknowledgedAt, String acknowledgedNote) {}
}
