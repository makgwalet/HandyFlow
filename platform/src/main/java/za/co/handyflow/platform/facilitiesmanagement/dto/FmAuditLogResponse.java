package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record FmAuditLogResponse(
        UUID id, UUID actorUserId, String actorName, String action, String entityType,
        UUID entityId, String details, Instant createdAt
) {}