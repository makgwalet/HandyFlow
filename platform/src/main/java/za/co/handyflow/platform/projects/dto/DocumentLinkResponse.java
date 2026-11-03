package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectDocumentLink;

import java.time.Instant;
import java.util.UUID;

public record DocumentLinkResponse(UUID id, UUID documentId, String linkType, UUID targetId,
                                   String note, String linkedByName, Instant linkedAt) {
    public static DocumentLinkResponse of(ProjectDocumentLink l) {
        return new DocumentLinkResponse(l.getId(), l.getDocumentId(), l.getLinkType(), l.getTargetId(),
                l.getNote(), l.getLinkedByName(), l.getLinkedAt());
    }
}
