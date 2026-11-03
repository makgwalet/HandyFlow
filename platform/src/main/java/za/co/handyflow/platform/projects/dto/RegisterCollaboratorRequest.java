package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;

public record RegisterCollaboratorRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters") String name,

        @Size(max = 255, message = "Organisation must not exceed 255 characters") String organisation,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 255, message = "Email must not exceed 255 characters") String email,

        @Min(value = 1, message = "Link expiry must be at least 1 day")
        @Max(value = 730, message = "Link expiry must not exceed 730 days") Integer expiryDays
) {}
