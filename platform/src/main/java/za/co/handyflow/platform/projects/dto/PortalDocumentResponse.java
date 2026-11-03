package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.time.Instant;

/**
 * Client-safe register entry. Deliberately NO file URL: the portal is reached by an unauthenticated
 * token link, and a storage URL could be a long-lived signed link. Clients see what exists and its
 * approval state; actual file delivery needs an authenticated, expiring download endpoint (later).
 */
public record PortalDocumentResponse(
        String documentNumber, String title, String revision, String documentType, Instant approvedAt) {
    public static PortalDocumentResponse of(ProjectDocument d) {
        return new PortalDocumentResponse(d.getDocumentNumber(), d.getTitle(), d.getRevision(),
                d.getDocumentType(), d.getApprovedAt());
    }
}