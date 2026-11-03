package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.time.Instant;
import java.util.UUID;

/**
 * Client-safe register entry. Deliberately NO file URL: the portal is reached by an unauthenticated
 * token link, and a storage URL could be a long-lived signed link. Clients see what exists and its
 * approval state. When the revision is a stored file, downloadable is true and the file is fetched
 * from the portal's own download endpoint (/portal/{token}/documents/{id}/download), which checks the
 * token, the project, the CLIENT visibility and the APPROVED status again on every request.
 */
public record PortalDocumentResponse(
        UUID id, String documentNumber, String title, String revision, String documentType,
        Instant approvedAt, boolean downloadable) {
    public static PortalDocumentResponse of(ProjectDocument d) {
        return new PortalDocumentResponse(d.getId(), d.getDocumentNumber(), d.getTitle(), d.getRevision(),
                d.getDocumentType(), d.getApprovedAt(), d.hasStoredFile());
    }
}