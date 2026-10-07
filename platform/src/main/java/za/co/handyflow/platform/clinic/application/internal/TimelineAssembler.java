package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.TimelineEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Filters, orders and limits timeline events. Pure: no database, no Spring. */
final class TimelineAssembler {

    private TimelineAssembler() {}

    static final int MAX_LIMIT = 500;
    static final int DEFAULT_LIMIT = 200;
    static final Set<String> BILLING_KINDS = Set.of("CLAIM", "PAYMENT");
    static final Set<String> ALL_KINDS = Set.of("APPOINTMENT", "CONSULTATION", "PRESCRIPTION", "LAB", "CLAIM", "PAYMENT");

    /**
     * Newest first. {@code kinds} null or empty means every kind the caller may see; billing kinds are dropped
     * unless {@code includeBilling}. An event without a time cannot be placed on a timeline and is left out.
     */
    static List<TimelineEvent> select(List<TimelineEvent> all, Set<String> kinds, Instant from, Instant to,
                                      boolean includeBilling, Integer limit) {
        int cap = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return all.stream()
                .filter(e -> e != null && e.at() != null)
                .filter(e -> includeBilling || !BILLING_KINDS.contains(e.kind()))
                .filter(e -> kinds == null || kinds.isEmpty() || kinds.contains(e.kind()))
                .filter(e -> from == null || !e.at().isBefore(from))
                .filter(e -> to == null || e.at().isBefore(to))
                .sorted(Comparator.comparing(TimelineEvent::at).reversed())
                .limit(cap)
                .toList();
    }
}
