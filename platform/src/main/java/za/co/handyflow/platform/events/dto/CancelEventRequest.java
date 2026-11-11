package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Size;

/** Optional body for cancelling an event. Omitting the body keeps the old behaviour (no reason, no emails). */
public record CancelEventRequest(
        @Size(max = 500) String reason,
        boolean notifyGuests
) {}