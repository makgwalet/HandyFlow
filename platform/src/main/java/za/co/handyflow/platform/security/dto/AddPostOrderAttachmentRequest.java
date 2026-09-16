package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record AddPostOrderAttachmentRequest(@NotBlank String fileUrl, @NotBlank String fileName) {}
