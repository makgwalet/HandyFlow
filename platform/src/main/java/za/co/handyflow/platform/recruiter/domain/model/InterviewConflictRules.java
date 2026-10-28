package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whether an interview can be booked without putting someone in two places at once. Pure.
 * <p>
 * Two interviews clash when their time ranges overlap and they share a person: the interviewer, a panelist, or the candidate (an application).
 * Back to back is fine: one that ends at 14:00 does not clash with one that starts at 14:00.
 */
public final class InterviewConflictRules {
    private InterviewConflictRules() {}

    public static final int DEFAULT_MINUTES = 60, MIN_MINUTES = 15, MAX_MINUTES = 480;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(SAST);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(SAST);

    /** One booked interview: when it runs, which application (candidate) it is for, and which staff are on it. */
    public record Slot(UUID interviewId, UUID applicationId, Instant start, int minutes, Set<UUID> people) {
        public Instant end() { return start.plusSeconds(minutes * 60L); }
    }

    public static int minutesOrDefault(Integer minutes) { return minutes == null ? DEFAULT_MINUTES : minutes; }

    /** Why this length cannot be used, or null. No length means the default. */
    public static String durationProblem(Integer minutes) {
        if (minutes == null) return null;
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) return "The interview must run between " + MIN_MINUTES + " minutes and " + (MAX_MINUTES / 60) + " hours.";
        return null;
    }

    /**
     * Why {@code wanted} cannot be booked given the interviews already booked, or null when it can. {@code names} gives the display name of
     * each person; someone with no name is described generically. The interview being replaced (same id) is ignored.
     */
    public static String conflictProblem(Slot wanted, List<Slot> booked, Map<UUID, String> names) {
        if (wanted.start() == null) return null;
        List<Slot> clashes = booked.stream()
                .filter(b -> b.start() != null)
                .filter(b -> wanted.interviewId() == null || !wanted.interviewId().equals(b.interviewId()))
                .filter(b -> wanted.start().isBefore(b.end()) && b.start().isBefore(wanted.end()))
                .sorted(Comparator.comparing(Slot::start))
                .toList();
        for (Slot b : clashes) {
            if (wanted.applicationId() != null && wanted.applicationId().equals(b.applicationId()))
                return "This candidate already has an interview from " + range(b) + " that overlaps this time.";
        }
        for (Slot b : clashes) {
            for (UUID person : wanted.people()) {
                if (b.people().contains(person)) {
                    String who = names.get(person);
                    return (who == null || who.isBlank() ? "An interviewer" : who) + " is already booked for another interview from " + range(b) + ".";
                }
            }
        }
        return null;
    }

    private static String range(Slot s) {
        return TIME.format(s.start()) + " to " + TIME.format(s.end()) + " on " + DAY.format(s.start());
    }
}
