package za.co.handyflow.platform.clinic.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Pure rules for practitioner time off: what is acceptable, and how a clash is worded. */
final class TimeOffRules {

    static final Duration MAX_LENGTH = Duration.ofDays(90);
    static final int MAX_REASON = 200;

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH);

    /** One block of time off that overlaps a proposed booking. */
    record Block(Instant starts, Instant ends, String reason) {}

    private TimeOffRules() {}

    /** Returns the cleaned reason (null when blank) or throws IllegalArgumentException for an unusable block. */
    static String validate(Instant starts, Instant ends, String reason) {
        if (starts == null || ends == null) throw new IllegalArgumentException("Start and end are required");
        if (!ends.isAfter(starts)) throw new IllegalArgumentException("The end must be after the start");
        if (Duration.between(starts, ends).compareTo(MAX_LENGTH) > 0)
            throw new IllegalArgumentException("Time off can be at most " + MAX_LENGTH.toDays() + " days at a time");
        String r = reason == null ? null : reason.trim();
        if (r != null && r.isEmpty()) r = null;
        if (r != null && r.length() > MAX_REASON) throw new IllegalArgumentException("The reason is too long (at most " + MAX_REASON + " characters)");
        return r;
    }

    static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    static String message(String practitionerName, List<Block> blocks, ZoneId zone) {
        Block b = blocks.get(0);
        boolean sameDay = b.starts().atZone(zone).toLocalDate().equals(b.ends().atZone(zone).toLocalDate());
        String span = sameDay
                ? b.starts().atZone(zone).format(DAY_TIME) + "–" + b.ends().atZone(zone).format(TIME)
                : b.starts().atZone(zone).format(DAY_TIME) + " to " + b.ends().atZone(zone).format(DAY_TIME);
        String who = practitionerName == null || practitionerName.isBlank() ? "This practitioner" : practitionerName;
        String why = b.reason() == null ? "" : " (" + b.reason() + ")";
        String more = blocks.size() > 1 ? " and " + (blocks.size() - 1) + " more block(s)" : "";
        return who + " is away " + span + why + more + ".";
    }
}
