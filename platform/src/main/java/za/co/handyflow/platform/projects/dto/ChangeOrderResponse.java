package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ChangeOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChangeOrderResponse(
        UUID        id,
        UUID        projectId,
        String      changeNumber,
        String      title,
        String      description,
        String      reason,
        String      status,
        BigDecimal  costImpact,
        int         scheduleImpact,
        String      approvedByName,
        Instant     approvedAt,
        Instant     clientApprovedAt,
        String      rejectionReason,
        Instant     createdAt
) {
    public static ChangeOrderResponse of(ChangeOrder c) {
        return new ChangeOrderResponse(
                c.getId(), c.getProjectId(), c.getChangeNumber(), c.getTitle(),
                c.getDescription(), c.getReason(), c.getStatus(),
                c.getCostImpact(), c.getScheduleImpact(),
                c.getApprovedByName(), c.getApprovedAt(), c.getClientApprovedAt(),
                c.getRejectionReason(), c.getCreatedAt());
    }
}
