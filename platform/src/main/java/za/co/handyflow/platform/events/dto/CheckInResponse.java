package za.co.handyflow.platform.events.dto;

import java.time.Instant;

public record CheckInResponse(
        String result,          // SUCCESS, ALREADY_CHECKED_IN, CANCELLED_TICKET, NOT_FOUND
        String guestName,
        String tierName,
        String ticketNumber,
        Instant checkedInAt,
        long totalCheckedIn     // running count for the event
) {}