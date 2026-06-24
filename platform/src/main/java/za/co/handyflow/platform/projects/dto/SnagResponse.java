package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.SnagItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SnagResponse(
        UUID        id,
        UUID        projectId,
        UUID        taskId,
        String      snagNumber,
        String      title,
        String      description,
        String      location,
        String      severity,
        String      status,
        String      assignedToName,
        LocalDate   dueDate,
        List<String> photoUrls,
        Instant     resolvedAt,
        Instant     createdAt
) {
    public static SnagResponse of(SnagItem s) {
        return new SnagResponse(
                s.getId(), s.getProjectId(), s.getTaskId(), s.getSnagNumber(),
                s.getTitle(), s.getDescription(), s.getLocation(), s.getSeverity(), s.getStatus(),
                s.getAssignedToName(), s.getDueDate(), s.getPhotoUrls(),
                s.getResolvedAt(), s.getCreatedAt());
    }
}
