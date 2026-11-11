package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        String summary,
        String details,      // newline-separated change list, null when there is nothing to list
        String actorName,
        Instant occurredAt
) {}