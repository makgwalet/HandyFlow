package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;

public record AddMemberRequest(
        @NotNull(message = "User id is required")
        java.util.UUID userId,

        @Size(max = 255, message = "User name must not exceed 255 characters")
        String userName,

        @NotNull(message = "Role is required")
        ProjectMemberRole role
) {}