package za.co.handyflow.platform.fuel.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** The arrival check freshness rules, kept free of storage so the boundary can be tested exactly. All times are server times. */
public final class FuelArrivalCheckPolicy {
    private FuelArrivalCheckPolicy() {}

    /** Used when a company has not set its own window. */
    public static final int DEFAULT_FRESH_HOURS = 12;

    public enum Status {
        /** No check has been made. */ MISSING,
        /** The latest check found something that blocks. A blocked check is never released; the driver checks again once it is fixed. */ BLOCKED,
        /** Passed, and no older than the window. */ FRESH,
        /** Passed, but older than the window. */ STALE,
        /** Stale, and released by an authorised person within the window. */ RELEASED;

        /** Whether protected steps (offloading, final delivery record) may go ahead. */
        public boolean clear() { return this == FRESH || this == RELEASED; }
    }

    /** Reason codes a release must carry. */
    public static final List<String> RELEASE_REASONS = List.of("DRIVER_DELAYED", "SITE_QUEUE", "OFFLINE_AT_SITE", "RECHECK_NOT_POSSIBLE", "OTHER");

    /** A check exactly as old as the window is still fresh; one second older is stale. */
    public static Status status(Instant checkedAt, boolean allowed, Instant releasedAt, int freshHours, Instant now) {
        if (checkedAt == null) return Status.MISSING;
        if (!allowed) return Status.BLOCKED;
        Duration window = Duration.ofHours(freshHours);
        if (Duration.between(checkedAt, now).compareTo(window) <= 0) return Status.FRESH;
        if (releasedAt != null && Duration.between(releasedAt, now).compareTo(window) <= 0) return Status.RELEASED;
        return Status.STALE;
    }

    public static Instant expiresAt(Instant checkedAt, Instant releasedAt, int freshHours) {
        if (checkedAt == null) return null;
        Instant base = releasedAt != null && releasedAt.isAfter(checkedAt) ? releasedAt : checkedAt;
        return base.plus(Duration.ofHours(freshHours));
    }
}
