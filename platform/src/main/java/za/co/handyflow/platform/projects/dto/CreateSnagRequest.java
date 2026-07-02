package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSnagRequest(

        @NotBlank(message = "Snag title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2 000 characters")
        String description,

        @Size(max = 500, message = "Location must not exceed 500 characters")
        String location,

        @NotBlank(message = "Severity is required")
        String severity,            // LOW|MEDIUM|HIGH|CRITICAL

        UUID taskId,

        UUID assignedTo,

        @Size(max = 255, message = "Assigned-to name must not exceed 255 characters")
        String assignedToName,

        LocalDate dueDate

) {}
