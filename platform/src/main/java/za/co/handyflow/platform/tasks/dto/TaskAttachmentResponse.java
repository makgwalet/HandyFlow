package za.co.handyflow.platform.tasks.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskAttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        String uploadedByName,
        Instant createdAt
) {
}