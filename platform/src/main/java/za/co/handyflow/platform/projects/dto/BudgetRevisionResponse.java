package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectBudgetRevision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetRevisionResponse(
        UUID id, UUID budgetLineId, String revisionType,
        BigDecimal oldAmount, BigDecimal newAmount, BigDecimal delta,
        String reason, UUID transferId, UUID changeOrderId, String revisedByName, Instant revisedAt
) {
    public static BudgetRevisionResponse of(ProjectBudgetRevision r) {
        return new BudgetRevisionResponse(
                r.getId(), r.getBudgetLineId(), r.getRevisionType().name(),
                r.getOldAmount(), r.getNewAmount(), r.getNewAmount().subtract(r.getOldAmount()),
                r.getReason(), r.getTransferId(), r.getChangeOrderId(), r.getRevisedByName(), r.getRevisedAt());
    }
}