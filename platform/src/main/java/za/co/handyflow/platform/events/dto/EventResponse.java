package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventNumber,
        String title,
        String description,
        String eventType,
        String status,
        String venueName,
        String venueAddress,
        Integer venueCapacity,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        boolean isFree,
        boolean isPrivate,
        LocalDateTime registrationDeadline,
        UUID surveyId,
        String notes,
        Instant createdAt
) {}