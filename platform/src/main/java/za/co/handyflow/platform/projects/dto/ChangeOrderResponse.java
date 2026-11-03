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
        int         revision,
        BigDecimal  costImpact,
        int         scheduleImpact,
        String      scopeImpact,
        String      riskImpact,
        String      procurementImpact,
        String      approvedByName,
        Instant     approvedAt,
        String      rejectionReason,
        Instant     rejectedAt,
        String      withdrawnReason,
        Instant     withdrawnAt,
        Instant     clientApprovedAt,
        boolean     clientDisputeOpen,
        Instant     clientDisputedAt,
        String      clientDisputeReason,
        Instant     clientDisputeResolvedAt,
        String      clientDisputeResolution,
        Instant     appliedAt,
        Integer     baselineVersion,
        Instant     createdAt
) {
    public static ChangeOrderResponse of(ChangeOrder c) {
        return new ChangeOrderResponse(
                c.getId(), c.getProjectId(), c.getChangeNumber(), c.getTitle(),
                c.getDescription(), c.getReason(), c.getStatus(), c.getRevision(),
                c.getCostImpact(), c.getScheduleImpact(),
                c.getScopeImpact(), c.getRiskImpact(), c.getProcurementImpact(),
                c.getApprovedByName(), c.getApprovedAt(),
                c.getRejectionReason(), c.getRejectedAt(),
                c.getWithdrawnReason(), c.getWithdrawnAt(),
                c.getClientApprovedAt(), c.hasOpenClientDispute(),
                c.getClientDisputedAt(), c.getClientDisputeReason(),
                c.getClientDisputeResolvedAt(), c.getClientDisputeResolution(),
                c.getAppliedAt(), c.getBaselineVersion(), c.getCreatedAt());
    }
}