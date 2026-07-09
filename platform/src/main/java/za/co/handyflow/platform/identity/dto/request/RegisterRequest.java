package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

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
        String password,
        // NEW: RegisterPage.tsx has always collected this in a real input
        // field, but it was never included in the actual request payload
        // (and this DTO had no field to receive it even if it had been) —
        // confirmed silently dropped on both sides.
        String phone,
        // NEW: RegisterPage.tsx has already been extended (independently
        // of this fix) to collect and send both of these — an
        // industry-first "what kind of business are you?" picker and a
        // promo code field, both apparently built ahead of backend
        // support. Neither had a field to land in here, meaning both
        // were being silently dropped on every registration. Both
        // optional and stored as-is — see the migration's own comment
        // for why this is deliberately storage only, not validation or
        // discount application.
        String businessType,
        String promoCode,
        // WHY optional? Existing clients not sending moduleKeys still work.
        // New onboarding flow sends ["security", "fleet"] etc.
        List<String> moduleKeys
) {}