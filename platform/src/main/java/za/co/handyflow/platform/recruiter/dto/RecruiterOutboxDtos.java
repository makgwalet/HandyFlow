package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterOutboxDtos {
    private RecruiterOutboxDtos() {}

    public record FailedEmail(UUID id, UUID applicationId, String kind, String toEmail, String subject, int attempts, String lastError, Instant createdAt) {}
    public record OutboxView(int pending, int sent, int failed, List<FailedEmail> failures) {}
}
