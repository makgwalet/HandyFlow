// security/dto/UploadGuardDocumentRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadGuardDocumentRequest(
        @NotBlank String category,
        String fileUrl,
        String fileBase64,
        String fileName,
        String notes
) {}