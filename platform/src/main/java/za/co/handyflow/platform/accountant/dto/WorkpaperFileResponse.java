package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkpaperFileResponse(
        UUID id,
        UUID folderId,
        String fileName,
        String mimeType,
        Long fileSizeBytes,
        String reviewStatus,
        int versionNumber,
        UUID supersededBy,
        Instant createdAt
) {
}