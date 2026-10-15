package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditReportDtos {
    private AuditReportDtos() {}

    public record CreateRequest(UUID engagementId, String template, String title) {}
    public record SectionRequest(String title, String body) {}
    public record PersonRequest(String kind, UUID userId) {}
    public record ItemRequest(Integer sectionPosition, String comment) {}
    public record RespondRequest(String decision, String response) {}

    public record TemplateView(String id, String label, List<String> sections) {}
    public record SectionView(int position, String title, String body, boolean autoFindings) {}
    public record ItemView(UUID id, Integer sectionPosition, String comment, String raisedByName, Instant raisedAt, String status,
                           String response, String respondedByName, Instant respondedAt) {}
    public record PersonView(UUID id, String kind, UUID userId, String name, Instant addedAt, Instant firstViewedAt, Instant lastViewedAt, int viewCount) {}
    public record Summary(UUID id, UUID engagementId, String engagementName, int version, String title, String template, String status,
                          Instant createdAt, Instant issuedAt, int openItems) {}
    public record View(Summary summary, String contentHash, boolean manager, boolean canEdit, List<SectionView> sections, List<ItemView> items, List<PersonView> people) {}
}
