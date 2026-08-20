package za.co.handyflow.platform.controls.dto;

import java.time.Instant;
import java.util.UUID;

public record ControlExceptionResponse(
        UUID id,
        String sourceModule,
        String controlType,
        String relatedEntityType,
        UUID relatedEntityId,
        String severity,
        String description,
        String status,
        Instant detectedAt,
        String resolvedByName,
        Instant resolvedAt,
        String resolutionNotes
) {}