package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * minutesOpen and the two overdue flags are worked out each time the incident is read — nothing pushes an
 * alert — so a screen showing incidents must make them hard to miss. timeline is filled for a single
 * incident and null in lists.
 */
public record IncidentResponse(
        UUID id,
        String reference,
        String title,
        String description,
        String category,
        String severity,
        String status,                       // OPEN | IN_PROGRESS | ESCALATED | RESOLVED | CLOSED
        String location,
        String reportedByName,
        String assignedToName,
        int escalationLevel,                 // 0-3
        Instant escalatedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String resolution,
        Instant createdAt,
        long minutesOpen,                    // until resolved, then how long it took
        boolean acknowledgementOverdue,      // nobody has taken it and its severity says someone should have
        boolean resolutionOverdue,
        List<IncidentNoteResponse> timeline
) {}