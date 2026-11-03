package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registers a document revision. We store references (a URL or storage key), not file bytes.
 *
 * documentNumber:
 *   - omitted  → a NEW document: a number is assigned (DWG-001, SUB-014, …).
 *   - supplied and it already exists → a NEW REVISION of that document: documentType must match
 *     and revision is required. Earlier unreviewed revisions are superseded immediately; an
 *     APPROVED one stays in force until this revision is approved.
 *   - supplied and new → a new document under your own number (e.g. the architect's DWG-A-101).
 *
 * fileUrl / fileName must not be an executable or script (see DocumentRules), and the URL may not
 * use javascript: / data: / vbscript: (stored-XSS guard, since it is rendered as a link).
 */
public record CreateDocumentRequest(

        @Pattern(
                regexp = "DRAWING|RFI|SUBMITTAL|CONTRACT|REPORT|PHOTO|GENERAL",
                message = "documentType must be one of: DRAWING, RFI, SUBMITTAL, CONTRACT, REPORT, PHOTO, GENERAL"
        )
        String documentType,

        @Size(max = 50, message = "Document number must be 50 characters or fewer")
        String documentNumber,

        @NotBlank(message = "Document title is required")
        @Size(max = 300, message = "Title must be 300 characters or fewer")
        String title,

        /** Revision label (e.g. "Rev A", "V2"). Required when adding a revision to an existing document. */
        @Size(max = 20, message = "Revision must be 20 characters or fewer")
        String revision,

        @NotBlank(message = "File URL is required")
        @Size(max = 2000, message = "File URL must be 2000 characters or fewer")
        @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript):).+$",
                message = "URL scheme is not allowed")
        String fileUrl,

        @Size(max = 300, message = "File name must be 300 characters or fewer")
        String fileName,

        Integer fileSizeKb,

        String description,

        @Pattern(regexp = "INTERNAL|CLIENT", message = "visibility must be INTERNAL or CLIENT")
        String visibility
) {
}