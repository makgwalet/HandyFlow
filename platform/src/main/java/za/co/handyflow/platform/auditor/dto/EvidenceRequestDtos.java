package za.co.handyflow.platform.auditor.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EvidenceRequestDtos {
    private EvidenceRequestDtos() {}

    public record CreateRequest(String title, String description, String priority, LocalDate dueDate, UUID assignedTo, UUID engagementId) {}
    public record AssignRequest(UUID assignedTo, LocalDate dueDate) {}
    public record TextRequest(String text) {}

    public record FileView(UUID id, String fileName) {}
    public record RoundView(int roundNo, String kind, String note, Instant submittedAt, String decision, String decisionNote,
                            Instant decidedAt, List<FileView> files) {}
    public record CommentView(UUID id, String authorKind, String authorLabel, String body, Instant createdAt) {}

    /** rounds and comments are filled in for a single request, left empty in lists. */
    public record RequestView(UUID id, String reference, UUID engagementId, String title, String description, String priority,
                              LocalDate dueDate, UUID assignedTo, String assignedToName, String requestedByKind, String requestedByLabel,
                              String status, int timesRejected, boolean overdue, boolean dueSoon, String cancelReason,
                              Instant createdAt, Instant closedAt, List<RoundView> rounds, List<CommentView> comments) {}
}
