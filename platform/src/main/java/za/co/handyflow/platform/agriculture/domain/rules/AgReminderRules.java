package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * When a reminder is sent, and who gets it. Pure.
 * <p>
 * Sending a reminder and a person dealing with it are two different facts. "Notified on" records that we told someone, and only the
 * scheduler writes it; "acknowledged" records that a person dealt with it, and only a person writes it. The attention list on the farm
 * dashboard shows everything not yet acknowledged, so a reminder stays on the list after the morning message until someone acts.
 */
public final class AgReminderRules {

    private AgReminderRules() {}

    /** A reminder still unacknowledged, or a stock shortage still unfixed, is repeated this many days after the last message. */
    public static final int REPEAT_DAYS = 7;

    /** The latest "notified on" date that is old enough to repeat the message. Used as a query parameter. */
    public static LocalDate repeatBefore(LocalDate today) { return today.minusDays(REPEAT_DAYS); }

    /**
     * Send a due-date reminder? Not before the due date; not if a person has dealt with it; the first time it is due; and then again
     * every {@value #REPEAT_DAYS} days while it is still open.
     */
    public static boolean shouldNotifyDue(LocalDate dueDate, boolean acknowledged, LocalDate notifiedOn, LocalDate today) {
        if (dueDate == null || acknowledged || dueDate.isAfter(today)) return false;
        return isNoticeDue(notifiedOn, today);
    }

    /** Never told, or last told {@value #REPEAT_DAYS} or more days ago. Also used for low-stock notices. */
    public static boolean isNoticeDue(LocalDate notifiedOn, LocalDate today) {
        return notifiedOn == null || !notifiedOn.isAfter(repeatBefore(today));
    }

    /** The farm manager if they can be reached, otherwise the fallback (the tenant admins). Never both, so nobody gets it twice. */
    public static <T> List<T> managerOrFallback(List<T> manager, List<T> fallback) {
        return manager != null && !manager.isEmpty() ? manager : (fallback == null ? List.of() : fallback);
    }

    /** "animal tag AN-001 (Bessie)" or "group batch B-12": what a message says instead of a database id. */
    public static String animalLabel(String tagNumber, String name) {
        String tag = tagNumber == null || tagNumber.isBlank() ? "(no tag)" : tagNumber.trim();
        return "animal tag " + tag + (name == null || name.isBlank() ? "" : " (" + name.trim() + ")");
    }

    public static String groupLabel(String batchNumber) {
        return "group batch " + (batchNumber == null || batchNumber.isBlank() ? "(no number)" : batchNumber.trim());
    }

    /** How overdue a reminder is, in words ("due today", "3 days overdue"). */
    public static String dueWording(LocalDate dueDate, LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
        if (days <= 0) return "due today";
        return days == 1 ? "1 day overdue" : days + " days overdue";
    }
}
