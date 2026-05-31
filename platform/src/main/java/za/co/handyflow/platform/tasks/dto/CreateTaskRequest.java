package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record CreateTaskRequest(
        @NotBlank String    title,
        String              description,
        String              priority,          // URGENT | HIGH | NORMAL | LOW  (default NORMAL)
        UUID                columnId,          // optional — defaults to first column
        UUID                assigneeId,
        String              assigneeName,      // display name until user lookup is wired
        LocalDate           dueDate,
        BigDecimal          estimatedHours,
        String              linkedEntityType,
        UUID                linkedEntityId
) {}