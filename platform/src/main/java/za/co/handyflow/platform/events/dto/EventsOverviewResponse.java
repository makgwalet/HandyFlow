package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tenant-wide events dashboard: how many events in each state, what's coming up, and what needs attention. */
public record EventsOverviewResponse(
        Map<String, Long> statusCounts,        // every status present, zero-filled, in lifecycle order
        List<UpcomingEvent> upcoming,          // LIVE now + not-yet-live events starting within the window
        List<AttentionItem> attention          // warnings first
) {
    public record UpcomingEvent(
            UUID id,
            String eventNumber,
            String title,
            String status,
            LocalDateTime startDatetime,
            long daysUntil,                    // negative = start date already passed
            Integer venueCapacity,
            long registered,
            BigDecimal fillPercent,            // null when the event has no capacity limit
            String budgetStatus                // null when the event has no budget
    ) {}

    /** type: DRAFT_STARTING_SOON | NOT_LIVE_ON_TIME | BUDGET_NOT_APPROVED | LOW_REGISTRATION | REGISTRATION_CLOSING | NEAR_CAPACITY */
    public record AttentionItem(
            String type,
            String severity,                   // WARNING | INFO
            String message,
            UUID eventId,
            String eventNumber
    ) {}
}