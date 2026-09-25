package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FindingResponse(
        UUID id, UUID engagementId, UUID sourceExceptionId, String title, String description,
        String rootCause, String recommendation, String managementResponse, String severity,
        UUID owner, String ownerName, LocalDate dueDate, String status,
        UUID createdBy, Instant createdAt, Instant resolvedAt,
        // FIX: closes the confirmed "no internal-audit engine reachable
        // by an external auditor" gap — appended at the end, matching
        // this session's own established convention for extending an
        // existing response.
        Instant closedAt, Instant previouslyClosedAt, Instant reopenedAt, String reopenReason,
        String externalVisibility, Instant sharedAt, UUID sharedBy, String sharingReason
) {}
