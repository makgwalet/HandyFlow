package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateScreeningRequest(
        @NotBlank String screeningType,  // POLYGRAPH | CRIMINAL_RECORD_CHECK | ...
        @NotBlank String reason,         // ONBOARDING | PERIODIC | POST_INCIDENT | ...
        String provider,                 // agency asked to do the check (optional)
        java.time.LocalDate requestedAt  // defaults to today when omitted
) {}
