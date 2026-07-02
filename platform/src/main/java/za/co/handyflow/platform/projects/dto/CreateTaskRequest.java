package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(

        @NotBlank(message = "Task title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        // Defaults handled by service (TASK if blank)
        String taskType,            // TASK|MILESTONE|SUMMARY

        // Defaults handled by service (MEDIUM if blank)
        String priority,            // LOW|MEDIUM|HIGH|CRITICAL

        UUID phaseId,

        UUID parentTaskId,

        UUID assigneeId,

        @Size(max = 255, message = "Assignee name must not exceed 255 characters")
        String assigneeName,

        LocalDate plannedStart,

        LocalDate plannedEnd,

        @DecimalMin(value = "0.25", message = "Estimated hours must be at least 15 minutes (0.25)")
        @DecimalMax(value = "9999.99", message = "Estimated hours is unrealistically high")
        BigDecimal estimatedHours,

        @DecimalMin(value = "0.00", message = "Budget amount cannot be negative")
        BigDecimal budgetAmount,

        boolean requiresInspection,

        @Size(max = 2000, message = "Notes must not exceed 2 000 characters")
        String notes

) {}
