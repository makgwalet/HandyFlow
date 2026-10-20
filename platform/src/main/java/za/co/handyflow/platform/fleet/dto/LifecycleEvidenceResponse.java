package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record LifecycleEvidenceResponse(
        UUID id,
        UUID eventId,
        String type,
        String description,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        Instant uploadedAt
) {}