package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponse(
        UUID    id,
        UUID    projectId,
        String  documentNumber,
        String  documentType,
        String  title,
        String  revision,
        String  fileUrl,
        String  fileName,
        Integer fileSizeKb,
        String  status,
        String  visibility,
        String  description,
        String  uploadedByName,
        UUID    supersededBy,
        String  approvedByName,
        Instant approvedAt,
        Instant voidedAt,
        String  voidReason,
        Instant createdAt,
        boolean storedFile,
        String  fileSha256,
        String  contentType,
        LocalDate retentionUntil,
        boolean legalHold
) {
    public static DocumentResponse of(ProjectDocument d) {
        return new DocumentResponse(
                d.getId(), d.getProjectId(), d.getDocumentNumber(), d.getDocumentType(), d.getTitle(),
                d.getRevision(), d.getFileUrl(), d.getFileName(), d.getFileSizeKb(),
                d.getStatus(), d.getVisibility(), d.getDescription(), d.getUploadedByName(),
                d.getSupersededBy(), d.getApprovedByName(), d.getApprovedAt(),
                d.getVoidedAt(), d.getVoidReason(), d.getCreatedAt(),
                d.hasStoredFile(), d.getFileSha256(), d.getContentType(),
                d.getRetentionUntil(), d.isLegalHold());
    }
}