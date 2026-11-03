package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.projects.domain.enums.DocumentLinkType;

import java.util.UUID;

/** Links a document revision to a record in the SAME project, as evidence. */
public record LinkDocumentRequest(
        @NotNull(message = "linkType is required") DocumentLinkType linkType,
        @NotNull(message = "targetId is required") UUID targetId,
        @Size(max = 500, message = "Note must not exceed 500 characters") String note
) {}
