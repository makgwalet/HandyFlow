package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.Project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        LocalDate   startDate,
        LocalDate   endDate,
        LocalDate   baselineStart,
        LocalDate   baselineEnd,
        BigDecimal  budgetTotal,
        BigDecimal  budgetSpent,
        BigDecimal  budgetCommitted,
        BigDecimal  contractValue,
        String      contractRef,
        String      contractType,
        BigDecimal  retentionPct,
        String      cidbGrade,
        String      nhbrcNumber,
        String      projectManagerName,
        String      clientPortalToken,
        boolean     portalEnabled,
        Instant     portalTokenExpiresAt,
        String      notes,
        // Governance
        UUID        sponsorId,
        String      sponsorName,
        String      objectives,
        String      scopeStatement,
        String      businessCase,
        Instant     proposedAt,
        Instant     approvedAt,
        String      approvedByName,
        Instant     completedAt,
        Instant     closedAt,
        String      closureNotes,
        String      lessonsLearned,
        String      cancellationReason,
        Long        version,
        Instant     createdAt,
        Instant     updatedAt,
        // Derived
        int         taskCount,
        int         completedTaskCount,
        int         openRiskCount,
        BigDecimal  budgetVariance,       // budgetTotal - budgetSpent - budgetCommitted
        List<String> availableActions,    // lifecycle actions valid right now (drives UI buttons)
        UUID        projectManagerId
) {
    public static ProjectResponse of(Project p, int taskCount, int completedTaskCount,
                                     int openRiskCount, List<String> availableActions) {
        BigDecimal variance = p.getBudgetTotal()
                .subtract(p.getBudgetSpent())
                .subtract(p.getBudgetCommitted());
        return new ProjectResponse(
                p.getId(), p.getProjectNumber(), p.getName(), p.getDescription(),
                p.getProjectType(), p.getStatus().name(), p.getHealth().name(),
                p.getClientName(), p.getClientId(), p.getSiteAddress(),
                p.getStartDate(), p.getEndDate(), p.getBaselineStart(), p.getBaselineEnd(),
                p.getBudgetTotal(), p.getBudgetSpent(), p.getBudgetCommitted(),
                p.getContractValue(), p.getContractRef(), p.getContractType(), p.getRetentionPct(),
                p.getCidbGrade(), p.getNhbrcNumber(), p.getProjectManagerName(),
                p.getClientPortalToken(), p.isPortalEnabled(), p.getPortalTokenExpiresAt(), p.getNotes(),
                p.getSponsorId(), p.getSponsorName(), p.getObjectives(),
                p.getScopeStatement(), p.getBusinessCase(),
                p.getProposedAt(), p.getApprovedAt(), p.getApprovedByName(),
                p.getCompletedAt(), p.getClosedAt(), p.getClosureNotes(), p.getLessonsLearned(),
                p.getCancellationReason(), p.getVersion(),
                p.getCreatedAt(), p.getUpdatedAt(),
                taskCount, completedTaskCount, openRiskCount, variance,
                availableActions != null ? availableActions : List.of(), p.getProjectManagerId());
    }
    /**
     * CHUNK 2b: copy for roles without cost visibility — removes budget, contract
     * value, retention, variance and the client-portal token. Health is kept (it is
     * an operational RAG status, though a RED can imply an overrun).
     */
    public ProjectResponse withoutSensitive() {
        return new ProjectResponse(
                id, projectNumber, name, description, projectType, status, health,
                clientName, clientId, siteAddress,
                startDate, endDate, baselineStart, baselineEnd,
                null, null, null,
                null, contractRef, contractType, null,
                cidbGrade, nhbrcNumber, projectManagerName,
                null, portalEnabled, portalTokenExpiresAt, notes,
                sponsorId, sponsorName, objectives, scopeStatement, businessCase,
                proposedAt, approvedAt, approvedByName, completedAt, closedAt,
                closureNotes, lessonsLearned, cancellationReason, version,
                createdAt, updatedAt,
                taskCount, completedTaskCount, openRiskCount, null, availableActions, projectManagerId);
    }
}