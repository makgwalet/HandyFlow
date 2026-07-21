package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

// Client management
// NEW: sendWelcomeEmail closes the "clientOnboardingWelcome() never
// called" gap — deliberately opt-in (defaults to false when omitted,
// since Java records deserialize a missing primitive boolean as
// false), not opt-out. A firm bulk-entering an existing book of
// clients into the system, not genuinely onboarding new ones, should
// never accidentally spam long-standing clients with a "welcome, your
// file has just been set up" email just because a checkbox defaulted
// the wrong way.
public record CreateClientRequest(
        @NotBlank String entityType,
        @NotBlank String tradingName,
        String registeredName,
        String registrationNumber,
        String taxReferenceNumber,
        String vatNumber,
        String vatCategory,
        @Min(1) @Max(12) int yearEndMonth,
        String contactEmail,
        String contactPhone,
        UUID crmCustomerId,
        boolean sendWelcomeEmail
) {
}