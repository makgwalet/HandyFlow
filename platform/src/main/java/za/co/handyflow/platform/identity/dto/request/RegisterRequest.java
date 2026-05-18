package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// WHY records for DTOs?
// Records are immutable by default — perfect for request objects
// that should never change after deserialization.
public record RegisterRequest(
        @NotBlank(message = "Company name is required")
        @Size(min = 2, max = 255, message = "Company name must be 2-255 characters")
        String companyName,

        @NotBlank(message = "Slug is required")
        @Pattern(regexp = "^[a-z0-9-]{3,100}$",
                message = "Slug must be lowercase letters, numbers and hyphens only")
        String slug,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {}
