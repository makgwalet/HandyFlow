package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * When, and in what words, the person running an employee relations case is reminded that a step is due. Pure: no loading, no sending.
 * <p>
 * A step due tomorrow gets one reminder the day before. A step that is late gets one the day after, then every seventh day. The words
 * carry the case number and the step only: an email is not a private place, and a case can be about discipline, health or a complaint
 * against a manager, so no name, type or subject goes into a message.
 */
public final class ErReminderRules {

    private ErReminderRules() {}

    public enum When { NONE, TOMORROW, LATE }

    public static When when(LocalDate due, LocalDate today) {
        if (due == null) return When.NONE;
        long days = ChronoUnit.DAYS.between(due, today);   // positive once the date has passed
        if (days == -1) return When.TOMORROW;
        if (days >= 1 && (days - 1) % 7 == 0) return When.LATE;
        return When.NONE;
    }

    /** What the step is called in a message. Anything unknown is called a case step. */
    public static String step(String dueLabel) {
        if (dueLabel == null) return "Case step";
        return switch (dueLabel) {
            case "Meeting" -> "Meeting";
            case "Response due" -> "Grievance response";
            case "Tell the employee" -> "Telling the employee the decision";
            case "Appeal period ends" -> "End of the appeal period";
            default -> "Case step";
        };
    }

    public static String title(String caseNumber, String dueLabel, When when) {
        return caseNumber + ": " + step(dueLabel).toLowerCase() + (when == When.TOMORROW ? " is due tomorrow" : " is late");
    }

    public static String message(String caseNumber, String dueLabel, LocalDate due, When when, LocalDate today) {
        String what = step(dueLabel);
        if (when == When.TOMORROW) return what + " for case " + caseNumber + " is due on " + due + ". Open the case in Employee relations.";
        long late = ChronoUnit.DAYS.between(due, today);
        return what + " for case " + caseNumber + " was due on " + due + " and is " + late + (late == 1 ? " day" : " days")
                + " late. Open the case in Employee relations.";
    }

    // ── The hearing: the chair, the person presenting the case and the employee ─────────────────

    public enum Role { CHAIR, INITIATOR, EMPLOYEE }

    /** A hearing that has been noticed, has not been held, and falls tomorrow. */
    public static boolean hearingTomorrow(String status, LocalDate hearingDate, LocalDate heldOn, LocalDate today) {
        return ErRules.NOTICE_ISSUED.equals(status) && heldOn == null && hearingDate != null && hearingDate.equals(today.plusDays(1));
    }

    public static String hearingTitle(String caseNumber) { return caseNumber + ": hearing tomorrow"; }

    /** Date and time only. The venue and the employee's rights are in the notice, and nothing says what the case is about. */
    public static String hearingMessage(Role role, String caseNumber, LocalDate date, LocalTime time) {
        String when = date + (time == null ? "" : " at " + time);
        return switch (role) {
            case CHAIR -> "You chair the hearing for case " + caseNumber + " on " + when + ". Open the case in Employee relations.";
            case INITIATOR -> "You present the case at the hearing for case " + caseNumber + " on " + when + ". Open the case in Employee relations.";
            case EMPLOYEE -> "Your hearing for case " + caseNumber + " is on " + when + ". The venue and your rights are in the notice you were given. "
                    + "If you cannot attend, tell HR today.";
        };
    }

    // ── Suspension and review dates ─────────────────────────────────────────────────────────────

    /** An open-ended precautionary suspension is questioned after this many days, then every seventh day. */
    public static final int OPEN_SUSPENSION_DAYS = 30;

    public enum Suspension { NONE, ENDS_TOMORROW, OPEN_LONG }

    /**
     * A suspension that has started: one reminder the day before it ends; and, when it has no end date, one at {@link #OPEN_SUSPENSION_DAYS}
     * days and weekly after that. Nothing after an end date: a suspension that has ended, whether on its planned date or because someone
     * ended it, leaves the same record, so a "has ended" reminder would nag about work already done.
     */
    public static Suspension suspension(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || from.isAfter(today)) return Suspension.NONE;
        if (to != null) {
            return when(to, today) == When.TOMORROW ? Suspension.ENDS_TOMORROW : Suspension.NONE;
        }
        long days = ChronoUnit.DAYS.between(from, today);
        return days >= OPEN_SUSPENSION_DAYS && (days - OPEN_SUSPENSION_DAYS) % 7 == 0 ? Suspension.OPEN_LONG : Suspension.NONE;
    }

    public static String suspensionTitle(String caseNumber, Suspension s) {
        return caseNumber + (s == Suspension.ENDS_TOMORROW ? ": suspension ends tomorrow" : ": suspension has no end date");
    }

    public static String suspensionMessage(String caseNumber, Suspension s, LocalDate from, LocalDate to, LocalDate today) {
        return switch (s) {
            case ENDS_TOMORROW -> "The suspension on case " + caseNumber + " ends on " + to + ". Decide whether to extend it or bring the employee back.";
            case OPEN_LONG -> "The suspension on case " + caseNumber + " has no end date and has run " + ChronoUnit.DAYS.between(from, today)
                    + " days. A precautionary suspension should be short. Set an end date or end it.";
            case NONE -> "";
        };
    }

    /** The review period of an incapacity case: the day before it ends, then late once it has ended without an outcome. */
    public static When review(String outcome, LocalDate reviewTo, LocalDate today) {
        return outcome != null ? When.NONE : when(reviewTo, today);
    }

    public static String reviewTitle(String caseNumber, When when) {
        return caseNumber + ": review period " + (when == When.TOMORROW ? "ends tomorrow" : "has ended");
    }

    public static String reviewMessage(String caseNumber, LocalDate reviewTo, When when) {
        return when == When.TOMORROW
                ? "The review period on case " + caseNumber + " ends on " + reviewTo + ". Prepare to assess the employee's progress."
                : "The review period on case " + caseNumber + " ended on " + reviewTo + " and no outcome has been recorded. Open the case in Employee relations.";
    }
}
