package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id, UUID actorUserId, String actorName, String action, String entityType,
        UUID entityId, String details, Instant createdAt
) {}