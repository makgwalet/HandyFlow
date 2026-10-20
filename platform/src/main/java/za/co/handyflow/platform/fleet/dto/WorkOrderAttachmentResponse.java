package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderAttachmentResponse(
        UUID id,
        String type,
        String description,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        Instant uploadedAt
) {}