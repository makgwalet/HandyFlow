package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /{projectId}/documents.
 *
 * fileUrl is required — we store document references (links to S3 / storage),
 * not the file bytes themselves.  Actual uploads go through a separate
 * pre-signed URL flow; this endpoint registers the resulting URL in the
 * document register.
 *
 * revision being non-null triggers the supersede flow in DocumentService:
 * any existing CURRENT / APPROVED doc of the same documentType is automatically
 * moved to SUPERSEDED before the new revision is saved as CURRENT.
 */
public record CreateDocumentRequest(

        /**
         * Document category — must match the DB check constraint.
         * Defaults to GENERAL if null (DocumentService / domain model handles default).
         */
        @Pattern(
                regexp = "DRAWING|RFI|SUBMITTAL|CONTRACT|REPORT|PHOTO|GENERAL",
                message = "documentType must be one of: DRAWING, RFI, SUBMITTAL, CONTRACT, REPORT, PHOTO, GENERAL"
        )
        String documentType,

        /** Human-readable document title — required, max 300 chars (matches DB column). */
        @NotBlank(message = "Document title is required")
        @Size(max = 300, message = "Title must be 300 characters or fewer")
        String title,

        /** Revision label (e.g. "Rev A", "Rev B", "V2"). Null = no revision tracking. */
        @Size(max = 20, message = "Revision must be 20 characters or fewer")
        String revision,

        /**
         * URL to the stored file — required.
         * This is typically an S3 / GCS URL returned by the storage service
         * after the client uploads the file directly via pre-signed URL.
         */
        @NotBlank(message = "File URL is required")
        String fileUrl,

        /** Original file name for display (e.g. "foundation-rev-a.pdf"). */
        @Size(max = 300, message = "File name must be 300 characters or fewer")
        String fileName,

        /** File size in kilobytes — for display and storage quota tracking. */
        Integer fileSizeKb,

        /** Optional longer description of the document's scope or content. */
        String description
) {
}