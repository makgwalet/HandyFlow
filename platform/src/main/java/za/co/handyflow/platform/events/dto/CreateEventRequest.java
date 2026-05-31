package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateEventRequest(
        @NotBlank String title,
        String description,
        String eventType,
        String venueName,
        String venueAddress,
        Integer venueCapacity,
        @NotNull LocalDateTime startDatetime,
        @NotNull LocalDateTime endDatetime,
        boolean isFree,
        boolean isPrivate,
        LocalDateTime registrationDeadline,
        String notes
) {}