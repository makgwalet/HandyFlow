package za.co.handyflow.platform.warehousing.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** The rules of dock appointments, with no I/O. */
public final class WhseDockRules {

    private WhseDockRules() {}

    public static final Set<String> LIVE = Set.of("SCHEDULED", "ARRIVED", "AT_DOCK");
    private static final Map<String, Set<String>> NEXT = Map.of(
            "SCHEDULED", Set.of("ARRIVED", "CANCELLED", "NO_SHOW"),
            "ARRIVED", Set.of("AT_DOCK", "CANCELLED"),
            "AT_DOCK", Set.of("COMPLETED"),
            "COMPLETED", Set.of(), "CANCELLED", Set.of(), "NO_SHOW", Set.of());

    public static String direction(String d) {
        String v = d == null ? "" : d.trim().toUpperCase();
        if (!v.equals("INBOUND") && !v.equals("OUTBOUND")) throw new IllegalArgumentException("Choose inbound (a delivery) or outbound (a collection).");
        return v;
    }

    public static void checkSlot(Instant start, Instant end) {
        if (start == null || end == null) throw new IllegalArgumentException("Enter the start and end of the slot.");
        if (!end.isAfter(start)) throw new IllegalArgumentException("The slot must end after it starts.");
        if (Duration.between(start, end).compareTo(Duration.ofHours(12)) > 0) throw new IllegalArgumentException("A slot is at most 12 hours.");
        if (Duration.between(start, end).toMinutes() < 5) throw new IllegalArgumentException("A slot is at least 5 minutes.");
        if (start.isAfter(Instant.now().plus(Duration.ofDays(366)))) throw new IllegalArgumentException("That is more than a year ahead.");
    }

    /** Two slots clash when they share any time. Back-to-back (one ends as the next starts) is fine. */
    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public static void checkTransition(String from, String to) {
        if (!NEXT.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("An appointment that is " + from.toLowerCase().replace('_', ' ') + " cannot be marked " + to.toLowerCase().replace('_', ' ') + ".");
        }
    }

    /** A no-show can only be recorded once the slot has started. */
    public static void checkNoShow(Instant start, Instant now) {
        if (now.isBefore(start)) throw new IllegalStateException("The slot has not started yet, so it cannot be a no-show. Cancel it instead.");
    }

    /** Minutes the vehicle arrived after the slot started (0 when on time or early). */
    public static long lateMinutes(Instant start, Instant arrived) {
        if (arrived == null || !arrived.isAfter(start)) return 0;
        return Duration.between(start, arrived).toMinutes();
    }
}
