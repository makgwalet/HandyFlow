package za.co.handyflow.platform.events.application.internal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The scheduling rules for the programme, as pure functions — no database, no clock, no Spring —
 * so every edge (back-to-back sessions, overnight, an empty programme) can be tested directly.
 */
final class SessionLogic {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private SessionLogic() {}

    /** A session reduced to what the rules need. Pass only sessions that are NOT cancelled. */
    record Slot(UUID id, String title, String room, String speaker, LocalDateTime start, LocalDateTime end) {}

    /** What the run sheet needs to know about each session. */
    record RunItem(UUID id, String status, LocalDateTime start, LocalDateTime end) {}

    /** The session in progress, the one up next, and how far behind the programme is. */
    record RunState(UUID currentId, UUID nextId, long runningLateMinutes) {}

    // ── Overlap ───────────────────────────────────────────────────────────────

    /**
     * True if the two time ranges share any time. Back-to-back sessions — one ending at 10:00, the
     * next starting at 10:00 — do NOT overlap; a programme that couldn't be tight would be unusable.
     */
    static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * Human-readable clashes between a candidate and the sessions already planned: the same room
     * used twice, or the same speaker in two places. Rooms and names are compared ignoring case and
     * surrounding spaces. A candidate is never in conflict with itself (matched by id), which is what
     * lets an edit keep its own slot.
     */
    static List<String> conflicts(Slot candidate, List<Slot> others) {
        List<String> found = new ArrayList<>();
        for (Slot o : others) {
            if (o.id().equals(candidate.id())) continue;
            if (!overlaps(candidate.start(), candidate.end(), o.start(), o.end())) continue;

            if (same(candidate.room(), o.room())) {
                found.add("Room \"" + o.room().trim() + "\" is already booked for \"" + o.title() + "\" ("
                        + HM.format(o.start()) + "–" + HM.format(o.end()) + ")");
            }
            if (same(candidate.speaker(), o.speaker())) {
                found.add(o.speaker().trim() + " is already speaking at \"" + o.title() + "\" ("
                        + HM.format(o.start()) + "–" + HM.format(o.end()) + ")");
            }
        }
        return found;
    }

    /** Whether a session sits fully inside the event's own start and end. */
    static boolean withinWindow(LocalDateTime start, LocalDateTime end, LocalDateTime eventStart, LocalDateTime eventEnd) {
        return !start.isBefore(eventStart) && !end.isAfter(eventEnd);
    }

    // ── Run state ─────────────────────────────────────────────────────────────

    /**
     * Where the day has got to.
     * <ul>
     *   <li><b>current</b> — the earliest IN_PROGRESS session;</li>
     *   <li><b>next</b> — the earliest SCHEDULED session (even if its start has already passed:
     *       that is a session that should have begun and hasn't);</li>
     *   <li><b>running late</b> — if the current session has overrun its planned end, by how much;
     *       otherwise, if nothing is running and the next session's start has passed, by how much;
     *       otherwise zero. Whole minutes, never negative.</li>
     * </ul>
     */
    static RunState runState(List<RunItem> items, LocalDateTime now) {
        RunItem current = items.stream()
                .filter(i -> "IN_PROGRESS".equals(i.status()))
                .min(Comparator.comparing(RunItem::start)).orElse(null);
        RunItem next = items.stream()
                .filter(i -> "SCHEDULED".equals(i.status()))
                .min(Comparator.comparing(RunItem::start)).orElse(null);

        long late = 0;
        if (current != null && now.isAfter(current.end())) {
            late = Duration.between(current.end(), now).toMinutes();
        } else if (current == null && next != null && now.isAfter(next.start())) {
            late = Duration.between(next.start(), now).toMinutes();
        }
        return new RunState(current != null ? current.id() : null, next != null ? next.id() : null, late);
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && !a.isBlank() && !b.isBlank()
                && a.trim().equalsIgnoreCase(b.trim());
    }
}