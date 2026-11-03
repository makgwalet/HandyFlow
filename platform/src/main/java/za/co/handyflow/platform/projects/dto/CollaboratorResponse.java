package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectCollaborator;
import za.co.handyflow.platform.projects.domain.model.ProjectCollaboratorLog;

import java.time.Instant;
import java.util.UUID;

/** Staff view of a collaborator account. Never carries the link or any secret. */
public record CollaboratorResponse(
        UUID id, String name, String organisation, String email, String status,
        Instant expiresAt, Instant disabledAt, String disabledByName, String disabledReason,
        Instant emailSentAt, Instant lastLoginAt, Instant createdAt
) {
    /** ACTIVE, EXPIRED or DISABLED. */
    public static CollaboratorResponse of(ProjectCollaborator c, Instant now) {
        String status = c.isDisabled() ? "DISABLED" : c.getExpiresAt().isAfter(now) ? "ACTIVE" : "EXPIRED";
        return new CollaboratorResponse(c.getId(), c.getName(), c.getOrganisation(), c.getEmail(), status,
                c.getExpiresAt(), c.getDisabledAt(), c.getDisabledByName(), c.getDisabledReason(),
                c.getEmailSentAt(), c.getLastLoginAt(), c.getCreatedAt());
    }

    public record LogEntry(UUID id, String action, String detail, String ipAddress, String actorName, Instant occurredAt) {
        public static LogEntry of(ProjectCollaboratorLog l) {
            return new LogEntry(l.getId(), l.getAction(), l.getDetail(), l.getIpAddress(), l.getActorName(), l.getOccurredAt());
        }
    }
}
