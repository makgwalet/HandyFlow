package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest (
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        // WHY require tenantSlug on login?
        // In a multi-tenant system, the same email can exist in multiple tenants.
        // The slug tells us WHICH tenant the user belongs to.
        // Think of it like: email = username, slug = which company portal
        @NotBlank(message = "Tenant slug is required")
        String tenantSlug
){}
