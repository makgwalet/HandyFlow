package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record LogExitRequest(
        @NotBlank String deviceHardwareId,
        // When the phone recorded the departure, if it was saved offline first. Guard route only.
        java.time.Instant recordedAt
) {}
