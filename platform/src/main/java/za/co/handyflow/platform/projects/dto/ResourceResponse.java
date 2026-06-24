package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectResource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResourceResponse(
        UUID        id,
        UUID        projectId,
        UUID        taskId,
        String      resourceType,
        UUID        resourceId,
        String      resourceName,
        String      role,
        BigDecimal  allocationPct,
        LocalDate   startDate,
        LocalDate   endDate,
        BigDecimal  hourlyRate,
        BigDecimal  dailyRate,
        BigDecimal  plannedHours,
        BigDecimal  actualHours,
        Instant     createdAt
) {
    public static ResourceResponse of(ProjectResource r) {
        return new ResourceResponse(
                r.getId(), r.getProjectId(), r.getTaskId(), r.getResourceType(),
                r.getResourceId(), r.getResourceName(), r.getRole(), r.getAllocationPct(),
                r.getStartDate(), r.getEndDate(), r.getHourlyRate(), r.getDailyRate(),
                r.getPlannedHours(), r.getActualHours(), r.getCreatedAt());
    }
}
