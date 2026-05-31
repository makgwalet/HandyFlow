package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Email @NotBlank String email,
        @NotBlank        String tenantSlug
) {}
