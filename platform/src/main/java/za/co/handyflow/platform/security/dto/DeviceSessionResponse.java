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
        String  forcedCloseReason,
        // FIX (GAP-03, mobile gap report): a guard has no reliable way
        // to learn their own current siteId client-side — CreateIncidentRequest.siteId
        // is @NotNull, so an incident filed without this fails validation.
        // Confirmed trivial to add — the mapper already has the shift
        // object in scope for shiftSummary; this needed no new query.
        UUID    siteId
) {}
