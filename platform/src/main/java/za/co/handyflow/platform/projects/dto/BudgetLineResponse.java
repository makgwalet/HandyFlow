package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetLineResponse(
        UUID        id,
        UUID        projectId,
        UUID        phaseId,
        String      category,
        String      description,
        BigDecimal  budgetedAmount,
        BigDecimal  committedAmount,
        BigDecimal  actualAmount,
        BigDecimal  variance,
        boolean     isProvisional,
        boolean     isPrimeCost,
        int         sortOrder
) {
    public static BudgetLineResponse of(ProjectBudgetLine b) {
        return new BudgetLineResponse(
                b.getId(), b.getProjectId(), b.getPhaseId(), b.getCategory(), b.getDescription(),
                b.getBudgetedAmount(), b.getCommittedAmount(), b.getActualAmount(),
                b.getVariance(), b.isProvisional(), b.isPrimeCost(), b.getSortOrder());
    }
}
