package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** assigneeName is client-supplied and not verified against the identity module (no facade was visible). */
public record AssignRfiRequest(
        @NotNull(message = "Assignee is required") UUID assigneeId,
        @Size(max = 255, message = "Assignee name must not exceed 255 characters") String assigneeName
) {}