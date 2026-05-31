package za.co.handyflow.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadLogoRequest(
        @NotBlank String logoBase64,   // data:image/png;base64,... or raw base64
        String mimeType                // image/png, image/jpeg, image/svg+xml
) {}