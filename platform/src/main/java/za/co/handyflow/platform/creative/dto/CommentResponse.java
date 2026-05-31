package za.co.handyflow.platform.creative.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID    id,
        String  authorName,
        String  authorType,   // TEAM | CLIENT
        String  comment,
        Instant createdAt
) {}