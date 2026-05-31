package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;

public record AddDeliverableRequest(
        @NotBlank String fileBase64,
        @NotBlank String fileName,
        String fileType,
        Long   fileSize,
        String notes
) {}