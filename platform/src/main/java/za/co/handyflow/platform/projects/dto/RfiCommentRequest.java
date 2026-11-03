package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used for clarification requests and reopening — both need a stated reason. */
public record RfiCommentRequest(
        @NotBlank(message = "A comment is required")
        @Size(max = 4000, message = "Comment must not exceed 4 000 characters")
        String comment
) {}