package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UploadWorkpaperFileRequest(
        @NotNull UUID folderId,
        @NotBlank String fileName,
        String mimeType,
        Long fileSizeBytes,
        @NotBlank String fileContentBase64
) {}
