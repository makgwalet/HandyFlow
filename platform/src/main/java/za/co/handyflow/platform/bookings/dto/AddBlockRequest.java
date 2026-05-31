package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AddBlockRequest(
        UUID staffId,
        @NotNull LocalDate blockDate,
        LocalTime startTime,   // null = full day
        LocalTime endTime,
        String reason
) {}