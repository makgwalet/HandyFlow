package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID    id,
        UUID    projectId,
        String  documentType,
        String  title,
        String  revision,
        String  fileUrl,
        String  fileName,
        Integer fileSizeKb,
        String  status,
        String  description,
        String  uploadedByName,
        Instant createdAt
) {
    public static DocumentResponse of(ProjectDocument d) {
        return new DocumentResponse(
                d.getId(), d.getProjectId(), d.getDocumentType(), d.getTitle(),
                d.getRevision(), d.getFileUrl(), d.getFileName(), d.getFileSizeKb(),
                d.getStatus(), d.getDescription(), d.getUploadedByName(), d.getCreatedAt());
    }
}
