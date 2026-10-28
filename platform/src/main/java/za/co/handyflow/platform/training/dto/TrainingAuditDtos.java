package za.co.handyflow.platform.training.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TrainingAuditDtos {
    private TrainingAuditDtos() {}

    public record AuditEntry(UUID id, Instant occurredAt, String actorName, String action, String entityType, UUID entityId, String detail) {}
    public record AuditPage(List<AuditEntry> entries, boolean hasMore) {}
}
