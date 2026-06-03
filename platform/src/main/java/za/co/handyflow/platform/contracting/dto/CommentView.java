package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;
import java.util.UUID;

// Comment / amendment request
public record CommentView(
        UUID id,
        String authorName,
        String authorRole,
        boolean isAmendmentRequest,
        String comment,
        String clauseRef,        // optional — e.g. "Clause 3.2"
        boolean resolved,
        Instant createdAt
) {}
