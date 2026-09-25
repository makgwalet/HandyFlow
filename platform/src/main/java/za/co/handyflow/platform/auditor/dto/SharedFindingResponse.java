package za.co.handyflow.platform.auditor.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// FIX: closes the confirmed "no internal-audit engine reachable by an
// external auditor" gap. Deliberately a separate, narrower shape from
// internalaudit.dto.FindingResponse rather than reusing it directly —
// no owner/createdBy (internal staff identities), no
// sourceExceptionId (internal linkage into sampling/testing an
// external party has no access to anyway). Includes the reopen
// history fields so an auditor sees "Previously closed: ... Reopened:
// ... Reason: ..." rather than a silently different status than what
// they may have seen before, per the agreed design.
public record SharedFindingResponse(
        UUID id,
        String title,
        String description,
        String rootCause,
        String recommendation,
        String managementResponse,
        String severity,
        String status,
        LocalDate dueDate,
        Instant closedAt,
        Instant previouslyClosedAt,
        Instant reopenedAt,
        String reopenReason,
        Instant sharedAt,
        String sharingReason
) {}
