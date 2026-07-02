package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreatePhaseRequest(

        @NotBlank(message = "Phase name is required")
        @Size(max = 255, message = "Phase name must not exceed 255 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1 000 characters")
        String description,

        // sortOrder is overridden by SequenceService.nextSortOrder() atomically
        // on the server — client value is ignored if SequenceService is in use.
        // Keeping the field so clients can hint at desired ordering.
        @PositiveOrZero(message = "Sort order cannot be negative")
        int sortOrder,

        LocalDate startDate,

        LocalDate endDate

) {}
