package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-in from a native app. Same credentials as the web login, plus the phone's own install id. */
public record MobileLoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Password is required") String password,
        @NotBlank(message = "Tenant slug is required") String tenantSlug,
        @Size(max = 100) String deviceId
) {}
