package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Extra gates on firearm issue, kept pure so they can be tested without Spring.
 * Each returns null when fine, or the error code to refuse with.
 */
final class IssueChecks {

    /** A firearm may be collected this long before the shift starts. */
    static final Duration COLLECT_EARLY = Duration.ofHours(2);

    private static final Set<String> DEAD_SHIFT = Set.of("CANCELLED", "MISSED", "COMPLETED", "PULLED");

    private IssueChecks() {}

    /** Carrying a firearm on duty needs a current PSiRA registration. Expiring today is still valid. */
    static String psira(LocalDate today, String psiraNumber, LocalDate psiraExpiry) {
        if (psiraNumber == null || psiraNumber.isBlank() || psiraExpiry == null) return "PSIRA_NOT_ON_FILE";
        if (psiraExpiry.isBefore(today)) return "PSIRA_EXPIRED";
        return null;
    }

    /**
     * @param shiftGuardId guard the shift is rostered to; null when no shift was given
     * @param status       shift status name, or null when no shift was given
     */
    static String shift(boolean requireShift, UUID guardId, UUID shiftGuardId, String status,
                        Instant startAt, Instant endAt, Instant now) {
        if (status == null || shiftGuardId == null) return requireShift ? "SHIFT_REQUIRED" : null;
        if (!shiftGuardId.equals(guardId)) return "SHIFT_NOT_GUARDS";
        if (DEAD_SHIFT.contains(status)) return "SHIFT_NOT_ACTIVE";
        if (now.isBefore(startAt.minus(COLLECT_EARLY)) || now.isAfter(endAt)) return "SHIFT_NOT_NOW";
        return null;
    }
}
