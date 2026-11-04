package za.co.handyflow.platform.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AccessAuditEventResponse(
        UUID    id,
        UUID    actorId,
        String  actorName,
        String  action,
        String  targetType,
        UUID    targetId,
        String  targetLabel,
        String  beforeJson,
        String  afterJson,
        Instant occurredAt
) {}
