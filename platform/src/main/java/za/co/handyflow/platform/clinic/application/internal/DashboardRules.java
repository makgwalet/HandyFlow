package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/** Pure rules for the dashboard: which instants make up "today", and how statuses add up. */
final class DashboardRules {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Africa/Johannesburg");

    /** Booked or arrived, not yet seen: these are the people the clinic is still waiting to see. */
    static final Set<String> AWAITING = Set.of("SCHEDULED", "CONFIRMED", "CHECKED_IN", "TRIAGED");
    /** A future appointment worth showing as "next up". */
    static final Set<String> UPCOMING = Set.of("SCHEDULED", "CONFIRMED");

    private DashboardRules() {}

    /** The local calendar date in the clinic's zone. */
    static LocalDate localDate(Instant now, ZoneId zone) {
        return now.atZone(zone).toLocalDate();
    }

    /** Start of the clinic's day containing {@code now}, as an instant. */
    static Instant dayStart(Instant now, ZoneId zone) {
        return localDate(now, zone).atStartOfDay(zone).toInstant();
    }

    /** Start of the next clinic day (exclusive end of today); correct on 23 or 25 hour days. */
    static Instant dayEnd(Instant now, ZoneId zone) {
        return localDate(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
    }

    static int count(Map<String, Integer> byStatus, String... statuses) {
        int n = 0;
        for (String s : statuses) n += byStatus.getOrDefault(s, 0);
        return n;
    }

    static int total(Map<String, Integer> byStatus) {
        return byStatus.values().stream().mapToInt(Integer::intValue).sum();
    }

    static int awaiting(Map<String, Integer> byStatus) {
        return count(byStatus, AWAITING.toArray(new String[0]));
    }
}
