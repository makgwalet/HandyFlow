package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;

/**
 * Reminders for two kinds of waiting work: letters the employee has not acknowledged, and performance goals that are late or that
 * nobody has approved. Pure. A letter's wording never says what it is about: letters can concern discipline or health, and an email
 * is not a private place.
 */
public final class WorkReminderRules {

    private WorkReminderRules() {}

    /** An issued letter that asks for acknowledgement is chased from this many days after it was issued. */
    public static final int LETTER_GRACE_DAYS = 7;
    /** A goal proposal waits this many days for its manager before the manager is reminded. */
    public static final int PROPOSAL_GRACE_DAYS = 5;
    /** Weekly chasing stops this many days after the date it started from; by then a person, not a message, has to act. */
    public static final int STOP_AFTER_DAYS = 56;

    private static boolean within(LocalDate due, LocalDate today) { return ReminderCadence.daysLate(due, today) <= STOP_AFTER_DAYS; }

    public static boolean letterReminderToday(String status, boolean requiresAck, Object acknowledgedAt, LocalDate issuedOn, LocalDate today) {
        if (!LetterRules.mayAcknowledge(status, requiresAck, acknowledgedAt) || issuedOn == null) return false;
        LocalDate due = issuedOn.plusDays(LETTER_GRACE_DAYS);
        return ReminderCadence.dueToday(due, today, true) && within(due, today);
    }

    /** An approved goal whose due date has passed and which nobody has closed. */
    public static boolean goalOverdueToday(String status, LocalDate dueDate, LocalDate today) {
        return PerfRules.ACTIVE.equals(status) && ReminderCadence.dueToday(dueDate, today, true) && within(dueDate, today);
    }

    /** A proposed goal nobody has approved for {@link #PROPOSAL_GRACE_DAYS} days. */
    public static boolean proposalReminderToday(String status, LocalDate proposedOn, LocalDate today) {
        if (!PerfRules.PROPOSED.equals(status) || proposedOn == null) return false;
        LocalDate due = proposedOn.plusDays(PROPOSAL_GRACE_DAYS);
        return ReminderCadence.dueToday(due, today, true) && within(due, today);
    }

    public static String letterTitle() { return "A letter is waiting for your acknowledgement"; }

    public static String letterMessage(String number, LocalDate issuedOn) {
        return "Letter " + number + ", issued on " + issuedOn + ", has not been acknowledged yet. Please open it and confirm that you received it.";
    }

    public static String goalOverdueTitle() { return "A goal of yours is past its date"; }

    public static String goalOverdueMessage(String goalTitle, LocalDate dueDate, LocalDate today) {
        long days = ReminderCadence.daysLate(dueDate, today);
        return "Your goal \"" + clip(goalTitle) + "\" was due on " + dueDate + " (" + days + (days == 1 ? " day" : " days")
                + " ago). Please record your progress, or talk to your manager about closing it.";
    }

    public static String proposalTitle(String employee) { return "Goal still waiting for approval: " + employee; }

    public static String proposalMessage(String employee, String goalTitle, LocalDate proposedOn) {
        return employee + " proposed the goal \"" + clip(goalTitle) + "\" on " + proposedOn + " and it has not been approved yet.";
    }

    private static String clip(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= 80 ? t : t.substring(0, 77) + "...";
    }
}
