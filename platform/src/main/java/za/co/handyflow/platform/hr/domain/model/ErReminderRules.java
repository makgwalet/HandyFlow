package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
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
}
