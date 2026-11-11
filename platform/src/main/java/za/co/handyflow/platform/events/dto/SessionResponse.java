package za.co.handyflow.platform.events.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String title,
        String description,
        String sessionType,
        String room,
        String speaker,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,                 // SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        Long delayMinutes,             // actual start minus planned start; null until it has started
        String notes
) {}