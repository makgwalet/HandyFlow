package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DocumentVisibilityRequest(
        @NotBlank(message = "visibility is required")
        @Pattern(regexp = "INTERNAL|CLIENT", message = "visibility must be INTERNAL or CLIENT")
        String visibility
) {}