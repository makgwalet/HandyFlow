package za.co.handyflow.platform.auditor.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AuditQueryDtos {
    private AuditQueryDtos() {}

    public record RaiseRequest(String kind, String title, String text, String subjectType, UUID subjectId, String subjectLabel, LocalDate dueDate) {}
    public record TextRequest(String text) {}
    public record LinkRequest(UUID findingId) {}

    public record MessageView(UUID id, String authorKind, String authorLabel, String messageKind, String body, Instant createdAt) {}
    /** linkedFindingTitle is shown to the business only; an auditor is told nothing about the finding. */
    public record QueryView(UUID id, String kind, String reference, UUID engagementId, String title, String subjectType, UUID subjectId,
                            String subjectLabel, LocalDate dueDate, String raisedByLabel, String status, int timesReopened, boolean overdue,
                            UUID linkedFindingId, String linkedFindingTitle, Instant createdAt, Instant closedAt, List<MessageView> messages) {}
}
