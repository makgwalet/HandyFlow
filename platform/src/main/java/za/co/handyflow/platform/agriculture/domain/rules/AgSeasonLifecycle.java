package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;

/**
 * Season rules that were previously unguarded: activate and close were plain assignments, so an active season could be
 * "activated" again, a planning season could be closed without ever running, and update() skipped the end-date check
 * that create() already had.
 * <p>
 * Deliberately NOT enforced: a single active season per farm. Farms legitimately run overlapping seasons (summer and
 * winter crops, irrigated and dryland blocks), so the web UI warns about a second active season instead of blocking it.
 * A season can be re-activated from CLOSED ("reopen").
 */
public final class AgSeasonLifecycle {

    private AgSeasonLifecycle() {}

    public static void requireCanActivate(String status) {
        if ("ACTIVE".equals(status)) {
            throw new IllegalStateException("season is already active");
        }
    }

    public static void requireCanClose(String status) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("only an active season can be closed (this one is " + status + ")");
        }
    }

    public static void requireOpenForNewCycles(String status) {
        if ("CLOSED".equals(status)) {
            throw new IllegalStateException("cannot add crop cycles to a closed season");
        }
    }

    public static void requireValidDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
    }
}
