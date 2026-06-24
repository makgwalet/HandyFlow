package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.TimeEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        UUID        projectId,
        UUID        taskId,
        String      userName,
        LocalDate entryDate,
        BigDecimal hours,
        String      description,
        BigDecimal  latitude,
        BigDecimal  longitude,
        String      status,
        String      approvedBy,
        Instant approvedAt,
        Instant     createdAt
) {
    public static TimeEntryResponse of(TimeEntry t) {
        return new TimeEntryResponse(
                t.getId(), t.getProjectId(), t.getTaskId(), t.getUserName(),
                t.getEntryDate(), t.getHours(), t.getDescription(),
                t.getLatitude(), t.getLongitude(), t.getStatus(),
                t.getApprovedBy() != null ? t.getApprovedBy().toString() : null,
                t.getApprovedAt(), t.getCreatedAt());
    }
}