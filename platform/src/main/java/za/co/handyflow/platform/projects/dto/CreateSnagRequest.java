package za.co.handyflow.platform.projects.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSnagRequest(
        String      title,          // required
        String      description,
        String      location,
        String      severity,       // LOW|MEDIUM|HIGH|CRITICAL
        UUID        taskId,
        UUID        assignedTo,
        String      assignedToName,
        LocalDate   dueDate
) {}
