package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A change order with its structured impact, its full submission history, and the RFIs that led to it. */
public record ChangeOrderDetailResponse(
        ChangeOrderResponse changeOrder,
        List<CostLineView> costLines,
        List<AffectedTaskView> affectedTasks,
        List<RevisionView> revisions,
        List<UUID> linkedRfiIds
) {
    public record CostLineView(UUID id, UUID budgetLineId, String category, String description, BigDecimal amount) {}

    public record AffectedTaskView(UUID taskId, String taskNumber, String title, int delayDays) {}

    public record RevisionView(int revision, String title, BigDecimal costImpact, int scheduleImpact,
                               String submittedByName, Instant submittedAt,
                               String outcome, String outcomeReason, Instant decidedAt) {}
}