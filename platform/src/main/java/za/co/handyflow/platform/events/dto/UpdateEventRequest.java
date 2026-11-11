package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Full-replace update (PUT semantics), same shape as CreateEventRequest. */
public record UpdateEventRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        String eventType,
        String venueName,
        String venueAddress,
        @Positive Integer venueCapacity,
        @NotNull LocalDateTime startDatetime,
        @NotNull LocalDateTime endDatetime,
        boolean isFree,
        boolean isPrivate,
        LocalDateTime registrationDeadline,
        String notes
) {}