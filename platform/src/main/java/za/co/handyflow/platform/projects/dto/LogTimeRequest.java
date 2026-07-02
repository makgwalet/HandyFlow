package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LogTimeRequest(

        // taskId may come from the request body when the endpoint is
        // POST /api/v1/projects/{projectId}/time rather than
        // POST /api/v1/projects/tasks/{taskId}/time.
        // @NotNull added so a missing taskId is caught at validation, not NPE at service.
        @NotNull(message = "Task ID is required")
        UUID taskId,

        // Null means "today" — service fills it in if absent
        LocalDate entryDate,

        @NotNull(message = "Hours worked is required")
        @DecimalMin(value = "0.25", message = "Minimum loggable time is 15 minutes (0.25 h)")
        @DecimalMax(value = "24.0",  message = "Cannot log more than 24 hours in a single entry")
        BigDecimal hours,

        @Size(max = 1000, message = "Description must not exceed 1 000 characters")
        String description,

        // GPS coords for mobile site check-in — optional
        @DecimalMin(value = "-90.0",  message = "Latitude must be between -90 and 90")
        @DecimalMax(value =  "90.0",  message = "Latitude must be between -90 and 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value =  "180.0", message = "Longitude must be between -180 and 180")
        BigDecimal longitude

) {}
