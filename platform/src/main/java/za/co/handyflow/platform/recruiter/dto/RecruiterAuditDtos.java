package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterAuditDtos {
    private RecruiterAuditDtos() {}

    public record AuditEntry(UUID id, Instant occurredAt, String actorName, String action, String entityType, UUID entityId, UUID applicationId, String detail) {}
    public record CvAccessRow(String actorName, int views, int candidates, java.time.Instant busiestHour, int busiestHourViews, boolean flagged) {}
    public record AuditPage(List<AuditEntry> entries, boolean hasMore) {}
}
