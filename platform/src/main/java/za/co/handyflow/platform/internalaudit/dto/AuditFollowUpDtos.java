package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditFollowUpDtos {
    private AuditFollowUpDtos() {}

    public record StartRequest(UUID engagementId, UUID priorEngagementId) {}
    public record AssessRequest(String outcome, String note) {}

    public record ItemView(UUID findingId, String title, String severity, String findingStatus, String outcome, String note, String assessedByName, Instant assessedAt) {}
    public record CountsView(int total, int assessed, int implemented, int partial, int notImplemented, int noLongerApplicable, int implementedPct, boolean complete) {}
    public record Summary(UUID engagementId, String engagementName, UUID priorEngagementId, String priorEngagementName, CountsView counts) {}
    public record View(Summary summary, List<ItemView> items) {}
}
