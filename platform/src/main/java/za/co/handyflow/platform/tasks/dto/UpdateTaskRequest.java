package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record UpdateTaskRequest(
        @Size(min = 1, max = 500) String title,
        String              description,
        String              priority,
        UUID                assigneeId,
        String              assigneeName,
        LocalDate           dueDate,
        BigDecimal          estimatedHours,
        String              linkedEntityType,
        UUID                linkedEntityId
) {}
