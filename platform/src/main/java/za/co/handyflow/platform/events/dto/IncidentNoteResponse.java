package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.util.UUID;

public record IncidentNoteResponse(
        UUID id,
        String kind,            // NOTE | ASSIGNMENT | ESCALATION | STATUS
        String note,
        String authorName,
        Instant createdAt
) {}