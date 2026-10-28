package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * When a work permit or passport that is running out is worth a message, and what it says. Pure: no database and no clock.
 * <p>
 * A document is mentioned 90, 60, 30, 14, 7 and 1 days before it ends and on the day. After that it is overdue and is mentioned
 * weekly for eight weeks, by which time a person has to act. A permit that has lapsed is more serious than a passport that has: the
 * wording says so. Messages name the person and the date, never a document number.
 */
public final class ForeignIdentityRules {

    private ForeignIdentityRules() {}

    public static final String PERMIT = "WORK_PERMIT", PASSPORT = "PASSPORT";
    public static final List<Integer> WARN_DAYS = List.of(90, 60, 30, 14, 7, 1, 0);
    public static final int STOP_AFTER_DAYS = 56, LISTED = 5;
    /** The furthest ahead the daily check needs to look. */
    public static final int LOOK_AHEAD_DAYS = 90;

    /** One document that is running out. */
    public record Expiring(String employee, String kind, LocalDate on) {}

    public static long daysLeft(LocalDate expiry, LocalDate today) { return ChronoUnit.DAYS.between(today, expiry); }

    public static boolean remindToday(LocalDate expiry, LocalDate today) {
        if (expiry == null) return false;
        long left = daysLeft(expiry, today);
        if (left >= 0) return WARN_DAYS.contains((int) Math.min(left, Integer.MAX_VALUE));
        return ReminderCadence.dueToday(expiry, today, true) && ReminderCadence.daysLate(expiry, today) <= STOP_AFTER_DAYS;
    }

    public static String label(String kind) { return PERMIT.equals(kind) ? "work permit" : "passport"; }

    /** "expires in 14 days", "expires tomorrow", "expires today", "expired 3 days ago". */
    public static String when(LocalDate expiry, LocalDate today) {
        long left = daysLeft(expiry, today);
        if (left > 1) return "expires in " + left + " days";
        if (left == 1) return "expires tomorrow";
        if (left == 0) return "expires today";
        long late = -left;
        return "expired " + late + (late == 1 ? " day ago" : " days ago");
    }

    public static boolean lapsed(LocalDate expiry, LocalDate today) { return daysLeft(expiry, today) < 0; }

    // ── The employee ────────────────────────────────────────────────────────────────────────────

    public static String employeeTitle(String kind, LocalDate expiry, LocalDate today) {
        return lapsed(expiry, today) ? "Your " + label(kind) + " has expired" : "Your " + label(kind) + " is about to expire";
    }

    public static String employeeMessage(String kind, LocalDate expiry, LocalDate today) {
        String base = "Your " + label(kind) + " " + when(expiry, today) + " (" + expiry + "). ";
        return base + (PERMIT.equals(kind)
                ? "Please give HR your renewed permit or proof that you applied for renewal, so your right to work stays on record."
                : "Please renew it and give HR the new passport details.");
    }

    // ── HR ──────────────────────────────────────────────────────────────────────────────────────

    public static String hrTitle(int count) { return "Work permits and passports needing attention (" + count + ")"; }

    public static String hrMessage(List<Expiring> items, LocalDate today) {
        List<Expiring> sorted = items.stream().sorted(Comparator.comparing(Expiring::on).thenComparing(Expiring::employee)).toList();
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(LISTED, sorted.size());
        for (int i = 0; i < shown; i++) {
            Expiring x = sorted.get(i);
            if (i > 0) sb.append("; ");
            sb.append(x.employee()).append(": ").append(label(x.kind())).append(' ').append(when(x.on(), today));
        }
        if (sorted.size() > shown) sb.append("; and ").append(sorted.size() - shown).append(" more");
        boolean lapsedPermit = sorted.stream().anyMatch(x -> PERMIT.equals(x.kind()) && lapsed(x.on(), today));
        sb.append('.');
        if (lapsedPermit) sb.append(" Someone whose work permit has lapsed may not lawfully keep working: act today.");
        return sb.toString();
    }
}
