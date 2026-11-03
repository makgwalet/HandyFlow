package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Size;

/** comment: required for REJECT and VOID; optional (recorded) for SUBMIT_REVIEW and APPROVE. */
public record DocumentActionRequest(
        @Size(max = 2000, message = "Comment must not exceed 2 000 characters")
        String comment
) {}