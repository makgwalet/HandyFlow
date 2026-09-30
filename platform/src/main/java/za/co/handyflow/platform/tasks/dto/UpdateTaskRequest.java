package za.co.handyflow.platform.tasks.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A null field means "leave unchanged". To REMOVE a value, set the matching clear flag (a plain
 * null cannot tell "no change" from "remove it"). Omitted flags default to false, so existing
 * clients keep their current behaviour.
 */
public record UpdateTaskRequest(
        @Size(min = 1, max = 500) String title,
        String              description,
        @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        UUID                assigneeId,
        String              assigneeName,
        LocalDate           dueDate,
        @DecimalMin("0") @DecimalMax("9999.99") BigDecimal estimatedHours,
        @Size(max = 30) String linkedEntityType,
        UUID                linkedEntityId,
        boolean             clearAssignee,
        boolean             clearDueDate,
        boolean             clearDescription,
        boolean             clearEstimatedHours,
        boolean             clearLink
) {}
