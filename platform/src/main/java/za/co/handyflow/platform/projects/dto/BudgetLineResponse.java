package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CHUNK 4: adds originalAmount (null = added after the baseline) and forecastToComplete
 * (null = no manual estimate). budgetedAmount is the CURRENT approved budget; variance is
 * budgeted − actual − committed.
 */
public record BudgetLineResponse(
        UUID        id,
        UUID        projectId,
        UUID        phaseId,
        String      category,
        String      description,
        BigDecimal  budgetedAmount,
        BigDecimal  originalAmount,
        BigDecimal  committedAmount,
        BigDecimal  actualAmount,
        BigDecimal  forecastToComplete,
        BigDecimal  variance,
        boolean     isProvisional,
        boolean     isPrimeCost,
        int         sortOrder
) {
    public static BudgetLineResponse of(ProjectBudgetLine b) {
        return new BudgetLineResponse(
                b.getId(), b.getProjectId(), b.getPhaseId(), b.getCategory(), b.getDescription(),
                b.getBudgetedAmount(), b.getOriginalAmount(), b.getCommittedAmount(), b.getActualAmount(),
                b.getForecastToComplete(), b.getVariance(), b.isProvisional(), b.isPrimeCost(), b.getSortOrder());
    }
}