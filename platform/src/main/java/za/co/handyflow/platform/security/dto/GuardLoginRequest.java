package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Guard login request — phone + PIN + optional device ID.
 *
 * WHY phone not email?
 * Most security guards in SA don't use work email addresses.
 * Phone is the identity anchor they actually know and use.
 * The phone number is already stored on security_guards.phone
 * from the existing Guard entity.
 */
public record GuardLoginRequest(
        @NotBlank @Pattern(regexp = "\\+?[0-9 ]{10,15}", message = "Invalid phone number")
        String phone,

        @NotBlank @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4-8 digits")
        String pin,

        String deviceId   // optional for Phase 1 — required in Phase 2 device binding
) {}