package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record InspectionPhotoResponse(
        UUID id,
        UUID resultId,        // null when attached to the inspection as a whole
        String fileName,
        String contentType,
        long sizeBytes,
        String caption,
        Instant uploadedAt
) {}