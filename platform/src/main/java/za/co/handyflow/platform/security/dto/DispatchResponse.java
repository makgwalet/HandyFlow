package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record DispatchResponse(
        UUID    id,
        UUID    alarmEventId,
        String  dispatchedUnitType,
        UUID    dispatchedGuardId,
        String  dispatchedGuardName,
        UUID    dispatchedBy,
        Instant dispatchedAt,
        Instant arrivedAt,
        Instant resolvedAt,
        Long    responseTimeMinutes,
        Long    resolutionTimeMinutes,
        String  outcome,
        String  resolutionNotes,
        boolean open
) {}
