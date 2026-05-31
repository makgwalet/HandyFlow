package za.co.handyflow.platform.desk.dto;

import java.time.Instant;
import java.util.UUID;

public record DeskCommentResponse(
        UUID    id,
        String  authorName,
        String  authorType,
        boolean internal,
        String  body,
        Instant createdAt
) {}
