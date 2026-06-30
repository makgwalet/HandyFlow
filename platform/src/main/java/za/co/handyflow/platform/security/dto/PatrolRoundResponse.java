package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record PatrolRoundResponse(
        UUID    id,
        UUID shiftId,
        int     roundNumber,
        String  status,
        Instant expectedStartAt,
        Instant expectedEndAt,
        Instant startedAt,
        Instant completedAt,
        int     checkpointsExpected,
        int     checkpointsScanned,
        double  completionPct,
        String  offScheduleReason
) {}