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
}
