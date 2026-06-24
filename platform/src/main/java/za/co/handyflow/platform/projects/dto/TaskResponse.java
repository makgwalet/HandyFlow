package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectTask;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID        projectId,
        UUID        phaseId,
        UUID        parentTaskId,
        String      taskNumber,
        String      title,
        String      description,
        String      taskType,
        String      status,
        String      priority,
        String      assigneeName,
        UUID        assigneeId,
        LocalDate plannedStart,
        LocalDate   plannedEnd,
        LocalDate   actualStart,
        LocalDate   actualEnd,
        Integer     durationDays,
        BigDecimal progressPct,
        BigDecimal  estimatedHours,
        BigDecimal  actualHours,
        boolean     isCritical,
        boolean     isMilestone,
        BigDecimal  budgetAmount,
        BigDecimal  actualCost,
        boolean     requiresInspection,
        Boolean     inspectionPassed,
        int         sortOrder,
        String      notes,
        Instant createdAt,
        Instant     updatedAt
) {
    public static TaskResponse of(ProjectTask t) {
        return new TaskResponse(
                t.getId(), t.getProjectId(), t.getPhaseId(), t.getParentTaskId(),
                t.getTaskNumber(), t.getTitle(), t.getDescription(),
                t.getTaskType(), t.getStatus(), t.getPriority(),
                t.getAssigneeName(), t.getAssigneeId(),
                t.getPlannedStart(), t.getPlannedEnd(), t.getActualStart(), t.getActualEnd(),
                t.getDurationDays(), t.getProgressPct(), t.getEstimatedHours(), t.getActualHours(),
                t.isCritical(), t.isMilestone(), t.getBudgetAmount(), t.getActualCost(),
                t.isRequiresInspection(), t.getInspectionPassed(),
                t.getSortOrder(), t.getNotes(), t.getCreatedAt(), t.getUpdatedAt());
    }
}