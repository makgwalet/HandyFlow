package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Live command-centre numbers for the door — cheap enough to poll every few seconds. */
public record AttendanceDashboardResponse(
        UUID eventId,
        String eventNumber,
        String title,
        String status,
        long expected,                         // live registrations, checked-in guests included
        long checkedIn,
        long remaining,                        // expected - checkedIn
        BigDecimal checkInRatePercent,         // null when nobody is expected
        long walkIns,
        Map<String, Long> scanResults,         // SUCCESS, WALK_IN, ALREADY_CHECKED_IN, CANCELLED_TICKET, TRANSFERRED_TICKET, NOT_FOUND
        List<TierAttendance> byTier,
        List<TimeBucket> arrivalsByQuarterHour,
        Instant firstAdmission,
        Instant lastAdmission,
        Instant generatedAt
) {
    public record TierAttendance(UUID tierId, String tierName, int registered, int checkedIn) {}

    /** start is the beginning of a 15-minute interval (UTC-aligned, which is also SAST-aligned). */
    public record TimeBucket(Instant start, long count) {}
}