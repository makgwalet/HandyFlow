package za.co.handyflow.platform.accounting.dto;

import java.time.Instant;
import java.util.UUID;

/** A posting that failed (finance patch F4). {@code retryable} is false when no journal could be built. */
public record PostingFailureResponse(
        UUID id,
        String sourceKey,
        String module,
        String summary,
        String reason,
        String status,
        int attempts,
        boolean retryable,
        Instant firstFailedAt,
        Instant lastFailedAt,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolutionNote,
        UUID journalEntryId
) {}
