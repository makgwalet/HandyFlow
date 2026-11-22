// security/application/internal/CoverageAttention.java
package za.co.handyflow.platform.security.application.internal;

import java.time.Instant;
import java.util.List;

/** Counts the posts that still need action today for the dashboard. Pure. */
public final class CoverageAttention {

    /** One post on one day: its state and when its last short stretch ends (null when covered). */
    public record Item(String state, Instant lastGapEnd) {}

    public record Counts(int uncovered, int shortOfGuards) {}

    private CoverageAttention() {}

    /** A post-day whose short stretches are all in the past can no longer be fixed, so it is not counted. */
    public static Counts count(List<Item> items, Instant now) {
        int none = 0, shortC = 0;
        for (Item i : items) {
            if (i.lastGapEnd() == null || !i.lastGapEnd().isAfter(now)) continue;
            if ("UNCOVERED".equals(i.state())) none++;
            else if ("SHORT".equals(i.state())) shortC++;
        }
        return new Counts(none, shortC);
    }
}
