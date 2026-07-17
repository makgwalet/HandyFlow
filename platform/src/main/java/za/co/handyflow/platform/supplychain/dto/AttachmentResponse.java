package za.co.handyflow.platform.supplychain.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        long fileSizeBytes,
        String uploadedByName,
        Instant createdAt
) {}