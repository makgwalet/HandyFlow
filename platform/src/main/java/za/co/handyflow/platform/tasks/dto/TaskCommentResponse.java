package za.co.handyflow.platform.tasks.dto;
import java.time.Instant;
import java.util.UUID;
public record TaskCommentResponse(
        UUID id, UUID authorId, String authorName, String body,
        Instant createdAt
) {}