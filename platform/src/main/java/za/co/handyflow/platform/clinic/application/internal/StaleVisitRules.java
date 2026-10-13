package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Set;

/**
 * Patch 0202: what to do with a visit from an earlier day that was never closed (still CHECKED_IN, TRIAGED or IN_PROGRESS).
 * Pure: the nightly sweep supplies the appointment's status and the statuses of the consultations recorded against it.
 * <ul>
 *   <li>A signed (or locked) consultation exists: the visit happened, so the appointment is COMPLETED.</li>
 *   <li>An unsigned consultation is still open: the clinical work is unfinished; it stays as it is and remains in the drafts tray.</li>
 *   <li>No consultation at all (or only abandoned ones): the patient arrived but was never seen; the appointment is NOT_SEEN.</li>
 * </ul>
 */
final class StaleVisitRules {

    enum Outcome { COMPLETE, NOT_SEEN, KEEP }

    static final Set<String> OPEN_VISIT = Set.of("CHECKED_IN", "TRIAGED", "IN_PROGRESS");
    private static final Set<String> DONE = Set.of("SIGNED", "LOCKED");
    private static final Set<String> GONE = Set.of("ABANDONED");

    private StaleVisitRules() {}

    /** Midnight at the start of today, clinic time: visits scheduled before this are from an earlier day. */
    static Instant startOfToday(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
    }

    static Outcome decide(String appointmentStatus, Instant scheduledAt, Instant startOfToday, Collection<String> consultationStatuses) {
        if (appointmentStatus == null || !OPEN_VISIT.contains(appointmentStatus)) return Outcome.KEEP;
        if (scheduledAt == null || !scheduledAt.isBefore(startOfToday)) return Outcome.KEEP;
        if (consultationStatuses.stream().anyMatch(DONE::contains)) return Outcome.COMPLETE;
        if (consultationStatuses.stream().anyMatch(s -> !GONE.contains(s))) return Outcome.KEEP;
        return Outcome.NOT_SEEN;
    }
}
