package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupCountLogResponse(
        UUID id,
        UUID groupId,
        String changeType,
        int delta,
        int countBefore,
        int countAfter,
        String reason,
        String sourceType,
        UUID sourceId,
        String createdByName,
        Instant createdAt
) {}
