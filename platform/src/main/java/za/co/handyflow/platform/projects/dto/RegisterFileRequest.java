package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The details sent with an uploaded file (the "meta" part of the multipart request; the file itself
 * is the "file" part). Numbering rules are the same as CreateDocumentRequest: leave documentNumber
 * out for a new document, or give an existing number plus a revision label for a new revision.
 */
public record RegisterFileRequest(

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

        @Size(max = 20, message = "Revision must be 20 characters or fewer")
        String revision,

        @Size(max = 2000, message = "Description must be 2 000 characters or fewer")
        String description,

        @Pattern(regexp = "INTERNAL|CLIENT", message = "visibility must be INTERNAL or CLIENT")
        String visibility
) {
}
