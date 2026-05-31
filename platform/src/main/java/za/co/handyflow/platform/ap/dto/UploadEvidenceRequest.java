package za.co.handyflow.platform.ap.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadEvidenceRequest(
        @NotBlank String fileBase64,
        @NotBlank String fileName
) {}
