package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One step in the vehicle's lifecycle history, with whichever detail record belongs to it. */
public record LifecycleEventResponse(
        UUID id,
        String eventType,
        String fromStatus,
        String toStatus,
        Instant occurredAt,
        Instant recordedAt,
        String reason,
        String note,
        UUID recordedBy,
        DisposalResponse disposal,       // set for DISPOSAL events
        TheftReportResponse theft,       // set for THEFT_REPORTED events
        WriteOffResponse writeOff,       // set for WRITE_OFF events
        List<LifecycleEvidenceResponse> evidence
) {}