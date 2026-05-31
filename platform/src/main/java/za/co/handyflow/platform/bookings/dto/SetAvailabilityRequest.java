package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;

public record SetAvailabilityRequest(
        UUID staffId,          // null = whole business
        @NotNull int dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {}