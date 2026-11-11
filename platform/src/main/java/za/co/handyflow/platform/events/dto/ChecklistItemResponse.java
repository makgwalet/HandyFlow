package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChecklistItemResponse(
        UUID id,
        String phase,
        String title,
        String description,
        String assignedTo,
        LocalDateTime dueAt,
        boolean required,
        boolean done,
        String doneByName,
        Instant doneAt,
        int sortOrder,
        boolean overdue         // not done, and its due time has passed
) {}
