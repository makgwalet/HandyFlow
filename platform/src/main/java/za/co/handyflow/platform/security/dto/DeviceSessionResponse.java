package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceSessionResponse(
        UUID    sessionId,
        UUID    deviceId,
        String  deviceName,
        UUID    guardId,
        String  guardName,
        UUID shiftId,
        String  shiftSummary,
        Instant startedAt,
        Instant endedAt,
        boolean open,
        Long    durationMinutes,
        String  handoverNotes,
        String  forcedCloseReason
) {}
