package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkpaperFileResponse(
        UUID id, UUID folderId, String fileName, String mimeType, Long fileSizeBytes,
        String reviewStatus, UUID preparedBy, Instant preparedAt, UUID reviewedBy, Instant reviewedAt,
        UUID signedOffBy, Instant signedOffAt, int versionNumber, UUID supersededBy, Instant createdAt
) {}
