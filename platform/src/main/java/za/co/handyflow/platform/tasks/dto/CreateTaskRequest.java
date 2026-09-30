package za.co.handyflow.platform.tasks.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// The limits below mirror the database (tasks.title VARCHAR(500), priority CHECK,
// estimated_hours NUMERIC(6,2), linked_entity_type VARCHAR(30)) so that bad input is a 400
// with a message, not a constraint violation surfacing as a 500.
public record CreateTaskRequest(
        @NotBlank @Size(max = 500) String title,
        String              description,
        @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,   // default NORMAL
        UUID                columnId,          // optional: defaults to the board's first column
        UUID                assigneeId,
        String              assigneeName,      // display name until user lookup is wired
        LocalDate           dueDate,
        @DecimalMin("0") @DecimalMax("9999.99") BigDecimal estimatedHours,
        @Size(max = 30) String linkedEntityType,
        UUID                linkedEntityId
) {}
