package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;

/**
 * Which performance reviews are worth a reminder today, and the words used. Pure. The words never carry a rating or a comment;
 * they only say whose turn it is.
 */
public final class PerfReminderRules {

    private PerfReminderRules() {}

    /** The employee has not written their self-review and its due date has passed. */
    public static boolean selfReminderToday(String reviewStatus, LocalDate selfDue, LocalDate today) {
        return PerfRules.AWAITING_SELF.equals(reviewStatus) && ReminderCadence.dueToday(selfDue, today, true);
    }

    /** The reviewer has not rated and the manager due date has passed. A review waiting for the employee still waits for the reviewer too. */
    public static boolean managerReminderToday(String reviewStatus, LocalDate managerDue, LocalDate today) {
        boolean waiting = PerfRules.AWAITING_SELF.equals(reviewStatus) || PerfRules.AWAITING_MANAGER.equals(reviewStatus);
        return waiting && ReminderCadence.dueToday(managerDue, today, true);
    }

    public static String selfDueTitle(String cycle) { return "Your self-review is due: " + cycle; }

    public static String selfOpenedMessage(String cycle, LocalDate selfDue) {
        return "The " + cycle + " review has opened. Please write your self-review by " + selfDue + ".";
    }

    public static String selfLateTitle(String cycle) { return "Your self-review is late: " + cycle; }

    public static String selfLateMessage(String cycle, LocalDate selfDue, LocalDate today) {
        long days = ReminderCadence.daysLate(selfDue, today);
        return "Your self-review for " + cycle + " was due on " + selfDue + " (" + days + (days == 1 ? " day" : " days") + " ago). Please complete it.";
    }

    public static String readyTitle(String employee) { return "Ready for your rating: " + employee; }

    public static String readyMessage(String employee, String cycle, LocalDate managerDue) {
        return employee + " has submitted their self-review for " + cycle + ". Your rating is due by " + managerDue + ".";
    }

    public static String releasedTitle(String cycle) { return "Your review result is ready: " + cycle; }

    public static String releasedMessage(String cycle) {
        return "The result of your " + cycle + " review has been released. Please read it and acknowledge it.";
    }

    public static String feedbackTitle(String subject) { return "Feedback requested: " + subject; }

    public static String feedbackMessage(String subject, String cycle) {
        return "You have been asked to give feedback on " + subject + " for the " + cycle + " review.";
    }

    public static String overdueTitle(int count) { return count + (count == 1 ? " review" : " reviews") + " waiting for your rating"; }

    public static String overdueMessage(String cycle, int count, LocalDate managerDue, LocalDate today) {
        long days = ReminderCadence.daysLate(managerDue, today);
        return count + (count == 1 ? " review" : " reviews") + " in the " + cycle + " cycle " + (count == 1 ? "is" : "are")
                + " past the date for your rating (" + managerDue + ", " + days + (days == 1 ? " day" : " days") + " ago).";
    }

    // ── Goals ─────────────────────────────────────────────────────────────────────────────────

    public static String goalProposedTitle(String employee) { return "Goal to approve: " + employee; }

    public static String goalProposedMessage(String employee, String goalTitle) {
        return employee + " has proposed a goal: \"" + clip(goalTitle) + "\". Please approve it, or talk to them about changing it.";
    }

    public static String goalApprovedTitle() { return "Your goal was approved"; }

    public static String goalApprovedMessage(String goalTitle) {
        return "Your goal \"" + clip(goalTitle) + "\" was approved. You can now record progress on it.";
    }

    public static String goalSetTitle() { return "A goal was set for you"; }

    public static String goalSetMessage(String goalTitle, LocalDate due) {
        return "A goal was set for you: \"" + clip(goalTitle) + "\", due " + due + ". You can record progress on it.";
    }

    // ── Improvement plans ─────────────────────────────────────────────────────────────────────

    /** A running plan should have a check-in at least this often. */
    public static final int CHECKIN_EVERY_DAYS = 14;

    /**
     * A running plan has gone {@link #CHECKIN_EVERY_DAYS} days without a check-in (counted from the last one, or from the day the employee was
     * told). The first reminder goes the day after, then weekly.
     */
    public static boolean checkinReminderToday(String planStatus, LocalDate told, LocalDate start, LocalDate lastCheckin, LocalDate today) {
        if (!PerfRules.PLAN_ACTIVE.equals(planStatus)) return false;
        LocalDate base = lastCheckin != null ? lastCheckin : (told != null ? told : start);
        return base != null && ReminderCadence.dueToday(base.plusDays(CHECKIN_EVERY_DAYS), today, true);
    }

    /** A plan is still running after its end date: someone must close it, extend it or cancel it. */
    public static boolean endedReminderToday(String planStatus, LocalDate endDate, LocalDate today) {
        return PerfRules.PLAN_ACTIVE.equals(planStatus) && ReminderCadence.dueToday(endDate, today, true);
    }

    // These go only to the plan's owner (or the administrators); they never carry the shortfall, the standard or a check-in note.
    public static String checkinTitle(String employee) { return "Check-in due: " + employee; }

    public static String checkinMessage(String employee, LocalDate lastCheckin, LocalDate today) {
        return "A check-in is due on the plan you own for " + employee + " ("
                + (lastCheckin == null ? "none has been recorded yet" : "the last one was " + ReminderCadence.daysLate(lastCheckin, today) + " days ago")
                + "). Please record it.";
    }

    public static String planEndedTitle(String employee) { return "Plan past its end date: " + employee; }

    public static String planEndedMessage(String employee, LocalDate endDate, LocalDate today) {
        long days = ReminderCadence.daysLate(endDate, today);
        return "The plan you own for " + employee + " ended on " + endDate + " (" + days + (days == 1 ? " day" : " days")
                + " ago) and is still running. Please close it, or ask HR to give it more time.";
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() <= 80 ? t : t.substring(0, 77) + "...";
    }
}
