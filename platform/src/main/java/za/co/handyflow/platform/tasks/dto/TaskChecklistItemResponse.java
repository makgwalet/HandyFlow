package za.co.handyflow.platform.tasks.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskChecklistItemResponse(
        UUID id,
        String text,
        boolean completed,
        int sortOrder,
        Instant createdAt,
        Instant completedAt
) {
}