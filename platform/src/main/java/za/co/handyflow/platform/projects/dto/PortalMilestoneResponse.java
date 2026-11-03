package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectTask;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Client-safe milestone view. Deliberately omits what TaskResponse carries:
 * budgetAmount, actualCost, estimated/actual hours, assignee identity, notes.
 */
public record PortalMilestoneResponse(
        String     taskNumber,
        String     title,
        String     status,
        LocalDate  plannedStart,
        LocalDate  plannedEnd,
        LocalDate  actualStart,
        LocalDate  actualEnd,
        BigDecimal progressPct
) {
    public static PortalMilestoneResponse of(ProjectTask t) {
        return new PortalMilestoneResponse(
                t.getTaskNumber(), t.getTitle(), t.getStatus(),
                t.getPlannedStart(), t.getPlannedEnd(),
                t.getActualStart(), t.getActualEnd(), t.getProgressPct());
    }
}