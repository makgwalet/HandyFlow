package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Edit a task's details from the board. Every field is optional: null leaves it as it is.
 * Dates are not here on purpose: they change through PUT /tasks/{id}/dates, which checks dependencies.
 *
 * assigneeId must be a member of the project. clearAssignee=true removes the assignee.
 */
public record UpdateTaskDetailsRequest(
        @Size(min = 1, max = 500, message = "Title must be 1 to 500 characters") String title,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH or CRITICAL") String priority,
        UUID assigneeId,
        Boolean clearAssignee,
        @DecimalMin(value = "0", message = "Estimated hours cannot be negative")
        @DecimalMax(value = "99999", message = "Estimated hours are out of range") BigDecimal estimatedHours,
        @Size(max = 4000, message = "Notes must not exceed 4 000 characters") String notes
) {}
