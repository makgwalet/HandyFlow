package za.co.handyflow.platform.events.dto;

import java.util.Map;

public record IncidentSummaryResponse(
        long total,
        long unresolved,
        long resolved,                              // resolved + closed
        long overdue,                               // unresolved and past their acknowledge or resolve target
        Map<String, Long> unresolvedBySeverity,     // LOW | MEDIUM | HIGH | CRITICAL, zero-filled
        Map<String, Long> unresolvedByStatus        // OPEN | IN_PROGRESS | ESCALATED, zero-filled
) {}