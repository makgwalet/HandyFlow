package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTaskRequest(
        String      title,
        String      priority,
        UUID        assigneeId,
        String      assigneeName,
        LocalDate   plannedStart,
        LocalDate   plannedEnd,
        BigDecimal  estimatedHours,
        BigDecimal  budgetAmount,
        boolean     requiresInspection,
        String      notes
) {}
