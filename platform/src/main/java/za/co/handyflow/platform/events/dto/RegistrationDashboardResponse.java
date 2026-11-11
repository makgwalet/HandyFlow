package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registration picture for one event. Payment figures are the amounts STAFF
 * recorded against guests — they are not reconciled to a payment provider
 * (that arrives with paid ticketing) and refunds are not modelled yet.
 */
public record RegistrationDashboardResponse(
        UUID eventId,
        String eventNumber,
        String title,
        String status,
        long registered,                       // live registrations (excludes cancelled / no-show / transferred-away)
        Integer venueCapacity,
        BigDecimal fillPercent,                // null when there is no capacity limit
        Long seatsRemaining,                   // null when there is no capacity limit
        long waitlisted,
        long cancelled,
        long transferred,
        long checkedIn,
        Map<String, Long> byStatus,
        Map<String, Long> bySource,            // STAFF | GROUP | WALK_IN | IMPORT | WAITLIST | TRANSFER
        List<TierRow> tiers,
        List<DayCount> registrationsByDay,
        BigDecimal amountRecorded,
        long paymentsPending                   // live guests on a paid event whose payment is still PENDING
) {
    public record TierRow(
            UUID id,
            String name,
            BigDecimal price,
            int quantity,
            int sold,
            int available,
            int checkedIn,
            BigDecimal faceValueSold           // price x sold — what the sales are worth at list price
    ) {}

    public record DayCount(LocalDate date, long count, long cumulative) {}
}