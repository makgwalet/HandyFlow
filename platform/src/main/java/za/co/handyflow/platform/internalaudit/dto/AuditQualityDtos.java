package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditQualityDtos {
    private AuditQualityDtos() {}

    public record RequestReview(UUID engagementId, UUID reviewerId) {}
    public record AnswerRequest(String result, String note) {}
    public record CompleteRequest(String conclusion, String note) {}
    public record CancelRequest(String reason) {}

    public record CheckView(String code, String area, String question, String result, String note, Instant answeredAt) {}
    public record Summary(UUID id, UUID engagementId, String engagementName, String reviewerName, UUID reviewerId, String requestedByName, Instant requestedAt,
                          String status, String conclusion, Instant completedAt, int answered, int total, int noAnswers) {}
    public record View(Summary summary, String conclusionNote, String cancelReason, List<CheckView> checks) {}
}
