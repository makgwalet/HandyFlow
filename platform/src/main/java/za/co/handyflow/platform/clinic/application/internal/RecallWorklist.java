package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.RecallPage;
import za.co.handyflow.platform.clinic.dto.RecallResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pure rules for working through recalls: what an action does, and how the list is filtered and paged. */
final class RecallWorklist {

    static final Set<String> TYPES = Set.of("CONTACT", "SNOOZE", "DISMISS", "REOPEN");
    static final Set<String> OUTCOMES = Set.of("REACHED", "NO_ANSWER", "LEFT_MESSAGE", "WRONG_NUMBER");
    static final int MAX_SNOOZE_DAYS = 180;
    static final int MAX_PAGE_SIZE = 100;

    private RecallWorklist() {}

    /** A stored action, newest first when in a list. */
    record Action(String type, String outcome, LocalDate snoozeUntil, Instant createdAt) {}

    record State(String status, LocalDate snoozedUntil, int attempts, Instant lastContactAt, String lastContactOutcome) {}

    /** Reads the history (newest first): the latest snooze/dismiss/reopen decides the status; contacts are counted. */
    static State stateOf(List<Action> newestFirst, LocalDate today) {
        String status = "OPEN";
        LocalDate until = null;
        for (Action a : newestFirst) {
            if (a.type().equals("CONTACT")) continue;
            if (a.type().equals("DISMISS")) status = "DISMISSED";
            else if (a.type().equals("SNOOZE") && a.snoozeUntil() != null && a.snoozeUntil().isAfter(today)) {
                status = "SNOOZED";
                until = a.snoozeUntil();
            }
            break; // SNOOZE that has expired, or REOPEN, means open
        }
        int attempts = 0;
        Instant lastAt = null;
        String lastOutcome = null;
        for (Action a : newestFirst) {
            if (!a.type().equals("CONTACT")) continue;
            attempts++;
            if (lastAt == null) { lastAt = a.createdAt(); lastOutcome = a.outcome(); }
        }
        return new State(status, until, attempts, lastAt, lastOutcome);
    }

    /** Throws IllegalArgumentException with a message fit for the user when the action is not valid. */
    static void validate(String type, String outcome, String note, LocalDate snoozeUntil, LocalDate today) {
        if (type == null || !TYPES.contains(type)) throw new IllegalArgumentException("Unknown action");
        switch (type) {
            case "CONTACT" -> {
                if (outcome == null || !OUTCOMES.contains(outcome)) throw new IllegalArgumentException("Choose what happened on the call");
            }
            case "SNOOZE" -> {
                if (snoozeUntil == null || !snoozeUntil.isAfter(today)) throw new IllegalArgumentException("Snooze until a future date");
                if (snoozeUntil.isAfter(today.plusDays(MAX_SNOOZE_DAYS))) throw new IllegalArgumentException("Snooze for at most " + MAX_SNOOZE_DAYS + " days");
            }
            case "DISMISS" -> {
                if (note == null || note.isBlank()) throw new IllegalArgumentException("Give a reason for dismissing the recall");
            }
            default -> { }
        }
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Note is too long (500 characters at most)");
    }

    /** filter: ALL (open only), OVERDUE, TODAY, NOT_CONTACTED, SNOOZED, DISMISSED. */
    static List<RecallResponse> filter(List<RecallResponse> all, String q, String filter, UUID practitionerId) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String f = filter == null || filter.isBlank() ? "ALL" : filter.toUpperCase(Locale.ROOT);
        return all.stream()
                .filter(r -> switch (f) {
                    case "SNOOZED" -> r.status().equals("SNOOZED");
                    case "DISMISSED" -> r.status().equals("DISMISSED");
                    case "OVERDUE" -> r.status().equals("OPEN") && r.overdueDays() > 0;
                    case "TODAY" -> r.status().equals("OPEN") && r.overdueDays() == 0;
                    case "NOT_CONTACTED" -> r.status().equals("OPEN") && r.contactAttempts() == 0;
                    default -> r.status().equals("OPEN");
                })
                .filter(r -> practitionerId == null || practitionerId.equals(r.practitionerId()))
                .filter(r -> needle.isEmpty()
                        || contains(r.patientName(), needle) || contains(r.patientPhone(), needle) || contains(r.diagnosis(), needle))
                .toList();
    }

    static RecallPage.Counts counts(List<RecallResponse> all) {
        int open = 0, overdue = 0, today = 0, fresh = 0, snoozed = 0;
        for (RecallResponse r : all) {
            if (r.status().equals("SNOOZED")) { snoozed++; continue; }
            if (!r.status().equals("OPEN")) continue;
            open++;
            if (r.overdueDays() > 0) overdue++; else today++;
            if (r.contactAttempts() == 0) fresh++;
        }
        return new RecallPage.Counts(open, overdue, today, fresh, snoozed);
    }

    static RecallPage page(List<RecallResponse> filtered, int page, int size, RecallPage.Counts counts) {
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int p = Math.max(0, page);
        int from = Math.min(p * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        return new RecallPage(filtered.subList(from, to), p, s, filtered.size(), counts);
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle);
    }
}
