package za.co.handyflow.platform.supplychain.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadAttachmentRequest(
        @NotBlank String fileName,
        String contentType,
        long fileSizeBytes,
        @NotBlank String fileContentBase64
) {}