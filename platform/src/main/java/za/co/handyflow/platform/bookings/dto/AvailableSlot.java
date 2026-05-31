package za.co.handyflow.platform.bookings.dto;

import java.time.LocalTime;

public record AvailableSlot(
        LocalTime startTime,
        LocalTime endTime,
        String displayLabel    // e.g. "09:00 – 10:00"
) {}