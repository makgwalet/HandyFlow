package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        String      title,          // required
        String      taskType,       // TASK|MILESTONE|SUMMARY — default TASK
        String      priority,       // LOW|MEDIUM|HIGH|CRITICAL — default MEDIUM
        UUID        phaseId,
        UUID        parentTaskId,
        UUID        assigneeId,
        String      assigneeName,
        LocalDate   plannedStart,
        LocalDate   plannedEnd,
        BigDecimal  estimatedHours,
        BigDecimal  budgetAmount,
        boolean     requiresInspection,
        String      notes
) {}
