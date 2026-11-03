package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.AccessLogView;

import java.time.Instant;
import java.util.UUID;

public record TransmittalAccessLogResponse(
        UUID id, UUID recipientId, UUID documentId, String revision, String action, String detail,
        String ipAddress, String actorName, Instant occurredAt
) {
    public static TransmittalAccessLogResponse of(AccessLogView l) {
        return new TransmittalAccessLogResponse(l.id(), l.recipientId(), l.documentId(), l.revision(),
                l.action(), l.detail(), l.ipAddress(), l.actorName(), l.occurredAt());
    }
}
