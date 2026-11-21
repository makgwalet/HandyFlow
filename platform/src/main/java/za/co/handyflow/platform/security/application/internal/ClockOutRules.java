package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.security.domain.model.ResourceCustody.ResourceType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/** Pure rules for clock-in matching and clock-out. No Spring, testable alone. */
final class ClockOutRules {

    private ClockOutRules() {}

    /**
     * Does an open custody list stop a clock-out?
     * - A firearm always blocks, whatever the caller claims.
     * - From the guard app, any open item blocks: the guard must return it
     *   in the app so the condition (good/damaged/missing) is recorded.
     * - From a supervisor, other items may be cleared with the flag.
     */
    static boolean blocksClockOut(Collection<ResourceType> open, boolean resourcesReturnedFlag, boolean guardRoute) {
        if (open.isEmpty()) return false;
        if (open.contains(ResourceType.FIREARM)) return true;
        if (guardRoute) return true;
        return !resourcesReturnedFlag;
    }

    /**
     * Is this scheduled shift the one to start now? Either it starts within the
     * window either side of now, or it has already started and not yet ended
     * (a guard who is later than the window is still on that shift).
     */
    static boolean shiftMatches(Instant start, Instant end, Instant now, int windowMinutes) {
        boolean inWindow = start.isAfter(now.minus(windowMinutes, ChronoUnit.MINUTES))
                && !start.isAfter(now.plus(windowMinutes, ChronoUnit.MINUTES));
        boolean running = !start.isAfter(now) && end != null && end.isAfter(now);
        return inWindow || running;
    }
}
