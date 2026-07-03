package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID assetId,
        String type,
        String severity,
        String title,
        String description,
        String operatorName,
        String siteName,
        Double latitude,
        Double longitude,
        String status,
        Instant reportedAt,
        Instant resolvedAt,
        String resolutionNotes
) {}