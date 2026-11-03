package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalAccessLog;

import java.time.Instant;
import java.util.UUID;

public record TransmittalAccessLogResponse(
        UUID id, UUID recipientId, UUID documentId, String revision, String action, String detail,
        String ipAddress, String actorName, Instant occurredAt
) {
    public static TransmittalAccessLogResponse of(ProjectTransmittalAccessLog l) {
        return new TransmittalAccessLogResponse(l.getId(), l.getRecipientId(), l.getDocumentId(), l.getRevision(),
                l.getAction(), l.getDetail(), l.getIpAddress(), l.getActorName(), l.getOccurredAt());
    }
}
