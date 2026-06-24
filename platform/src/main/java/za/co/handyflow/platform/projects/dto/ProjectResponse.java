package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.Project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String      projectNumber,
        String      name,
        String      description,
        String      projectType,
        String      status,
        String      health,
        String      clientName,
        UUID        clientId,
        String      siteAddress,
        LocalDate startDate,
        LocalDate   endDate,
        LocalDate   baselineStart,
        LocalDate   baselineEnd,
        BigDecimal budgetTotal,
        BigDecimal  budgetSpent,
        BigDecimal  budgetCommitted,
        BigDecimal  contractValue,
        String      contractRef,
        BigDecimal  retentionPct,
        String      cidbGrade,
        String      nhbrcNumber,
        String      projectManagerName,
        String      clientPortalToken,
        String      notes,
        Instant createdAt,
        Instant     updatedAt,
        // Derived
        int         taskCount,
        int         completedTaskCount,
        int         openRiskCount,
        BigDecimal  budgetVariance    // budgetTotal - budgetSpent - budgetCommitted
) {
    public static ProjectResponse of(Project p,
                                     int taskCount, int completedTaskCount, int openRiskCount) {
        BigDecimal variance = p.getBudgetTotal()
                .subtract(p.getBudgetSpent())
                .subtract(p.getBudgetCommitted());
        return new ProjectResponse(
                p.getId(), p.getProjectNumber(), p.getName(), p.getDescription(),
                p.getProjectType(), p.getStatus(), p.getHealth(),
                p.getClientName(), p.getClientId(), p.getSiteAddress(),
                p.getStartDate(), p.getEndDate(), p.getBaselineStart(), p.getBaselineEnd(),
                p.getBudgetTotal(), p.getBudgetSpent(), p.getBudgetCommitted(),
                p.getContractValue(), p.getContractRef(), p.getRetentionPct(),
                p.getCidbGrade(), p.getNhbrcNumber(), p.getProjectManagerName(),
                p.getClientPortalToken(), p.getNotes(),
                p.getCreatedAt(), p.getUpdatedAt(),
                taskCount, completedTaskCount, openRiskCount, variance);
    }
}