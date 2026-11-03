package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Size;

/** noChangeNote: only needed when the RFI is flagged as affecting schedule or cost but no change order handles it. */
public record CloseRfiRequest(
        @Size(max = 4000, message = "Note must not exceed 4 000 characters") String noChangeNote
) {}