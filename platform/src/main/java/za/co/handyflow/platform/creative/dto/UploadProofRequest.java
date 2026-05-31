package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadProofRequest(
        String     title,
        @NotBlank  String fileBase64,
        @NotBlank  String fileName,
        String fileType,
        String thumbnailBase64,
        String notes
) {}