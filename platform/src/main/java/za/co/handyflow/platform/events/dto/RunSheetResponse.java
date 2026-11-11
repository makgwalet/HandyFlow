package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.util.List;

/** The day's programme with a "you are here" marker. Cheap enough to poll from a stage-manager's tablet. */
public record RunSheetResponse(
        Instant generatedAt,
        List<SessionResponse> sessions,    // in time order, cancelled ones included and marked
        SessionResponse current,           // the session in progress, or null
        SessionResponse next,              // the earliest session still to start, or null
        long runningLateMinutes            // 0 when on time
) {}