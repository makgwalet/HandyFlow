package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        ProjectMemberRole role
) {}