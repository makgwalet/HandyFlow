package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(
        UUID    id,
        String  fromStatus,
        String  toStatus,
        String  action,
        String  reason,
        UUID    changedBy,
        String  changedByName,
        Instant changedAt
) {
    public static StatusHistoryResponse of(ProjectStatusHistory h) {
        return new StatusHistoryResponse(
                h.getId(),
                h.getFromStatus() != null ? h.getFromStatus().name() : null,
                h.getToStatus().name(),
                h.getAction(), h.getReason(),
                h.getChangedBy(), h.getChangedByName(), h.getChangedAt());
    }
}