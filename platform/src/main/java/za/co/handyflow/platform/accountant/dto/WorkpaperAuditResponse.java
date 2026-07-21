package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkpaperAuditResponse(
        UUID id,
        String eventType,
        UUID performedBy,
        Instant performedAt
) {
}