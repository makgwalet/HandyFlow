package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordConsentRequest(
        @NotBlank String consentType,
        @NotBlank String action,       // GRANTED | REVOKED
        String method,                  // VERBAL | WRITTEN | ELECTRONIC — optional
        String capturedByName,
        String notes
) {}