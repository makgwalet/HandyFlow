package za.co.handyflow.platform.clinic.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Pure booking rules: overlap, walk-in grace for "now", sensible durations, and the conflict wording. */
final class AppointmentRules {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Africa/Johannesburg");
    static final int DEFAULT_MINUTES = 30;
    static final int MIN_MINUTES = 5;
    static final int MAX_MINUTES = 480;
    /** A walk-in is booked "now", so a time a few minutes ago (typed a moment earlier) is still accepted. */
    static final Duration WALK_IN_GRACE = Duration.ofMinutes(15);

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** One existing booking that clashes with a new one. */
    record Clash(String patientName, Instant start, int minutes) {}

    private AppointmentRules() {}

    /** Half-open intervals: back-to-back bookings (one ends when the next starts) do not overlap. */
    static boolean overlaps(Instant startA, int minutesA, Instant startB, int minutesB) {
        Instant endA = startA.plus(Duration.ofMinutes(minutesA));
        Instant endB = startB.plus(Duration.ofMinutes(minutesB));
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    static int minutes(Integer requested) {
        if (requested == null) return DEFAULT_MINUTES;
        if (requested < MIN_MINUTES || requested > MAX_MINUTES) {
            throw new IllegalArgumentException("Appointment length must be between " + MIN_MINUTES + " and " + MAX_MINUTES + " minutes");
        }
        return requested;
    }

    /** Earlier than the walk-in grace before now. */
    static boolean inThePast(Instant scheduledAt, Instant now) {
        return scheduledAt.isBefore(now.minus(WALK_IN_GRACE));
    }

    static String conflictMessage(String practitionerName, List<Clash> clashes, ZoneId zone) {
        Clash c = clashes.get(0);
        String when = HM.format(c.start().atZone(zone)) + "–" + HM.format(c.start().plus(Duration.ofMinutes(c.minutes())).atZone(zone));
        String who = practitionerName == null || practitionerName.isBlank() ? "This practitioner" : practitionerName;
        String more = clashes.size() > 1 ? " (and " + (clashes.size() - 1) + " more)" : "";
        return who + " already has an appointment " + when + " with " + c.patientName() + more + ".";
    }

    /** The patient already has a booking at that time. Each clash carries the practitioner's name (may be null). */
    static String patientConflictMessage(String patientName, List<Clash> clashes, ZoneId zone) {
        Clash c = clashes.get(0);
        String when = HM.format(c.start().atZone(zone)) + "–" + HM.format(c.start().plus(Duration.ofMinutes(c.minutes())).atZone(zone));
        String who = patientName == null || patientName.isBlank() ? "This patient" : patientName;
        String with = c.patientName() == null || c.patientName().isBlank() ? "" : " with " + c.patientName();
        String more = clashes.size() > 1 ? " (and " + (clashes.size() - 1) + " more)" : "";
        return who + " already has an appointment " + when + with + more + ".";
    }

    /** The room is already in use at that time. */
    static String roomConflictMessage(String roomName, List<Clash> clashes, ZoneId zone) {
        Clash c = clashes.get(0);
        String when = HM.format(c.start().atZone(zone)) + "–" + HM.format(c.start().plus(Duration.ofMinutes(c.minutes())).atZone(zone));
        String room = roomName == null || roomName.isBlank() ? "This room" : roomName;
        String more = clashes.size() > 1 ? " (and " + (clashes.size() - 1) + " more)" : "";
        return room + " is already booked " + when + " for " + c.patientName() + more + ".";
    }
}
