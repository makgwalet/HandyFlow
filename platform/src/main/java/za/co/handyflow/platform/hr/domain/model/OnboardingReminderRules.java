package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * When an overdue onboarding task is worth a message, and what the message says. Pure: no database and no clock.
 * <p>
 * A task is mentioned the day after it falls due, and after that only every seventh day while it is still open and required.
 * Optional tasks are mentioned once. This keeps a long list of late tasks from becoming a daily message people learn to ignore.
 */
public final class OnboardingReminderRules {

    public static final int REPEAT_DAYS = ReminderCadence.REPEAT_DAYS;
    public static final int LISTED = 5;

    private OnboardingReminderRules() {}

    /** An open task that is past its due date. */
    public record Late(String title, boolean required, LocalDate due) {}

    public static long daysLate(LocalDate due, LocalDate today) {
        return ReminderCadence.daysLate(due, today);
    }

    public static boolean remindToday(boolean required, LocalDate due, LocalDate today) {
        return ReminderCadence.dueToday(due, today, required);
    }

    /** The tasks to mention today, most overdue first. */
    public static List<Late> toMention(List<Late> overdue, LocalDate today) {
        return overdue.stream().filter(t -> remindToday(t.required(), t.due(), today))
                .sorted((a, b) -> a.due().compareTo(b.due())).toList();
    }

    public static String title(String employeeName, int count) {
        return "Onboarding overdue: " + employeeName + " (" + count + " task" + (count == 1 ? "" : "s") + ")";
    }

    public static String message(String employeeName, List<Late> tasks, LocalDate today) {
        StringBuilder sb = new StringBuilder("Onboarding for ").append(employeeName).append(" has ").append(tasks.size())
                .append(tasks.size() == 1 ? " task" : " tasks").append(" past its due date: ");
        int shown = Math.min(LISTED, tasks.size());
        for (int i = 0; i < shown; i++) {
            Late t = tasks.get(i);
            long days = daysLate(t.due(), today);
            if (i > 0) sb.append("; ");
            sb.append(t.title()).append(" (").append(days).append(days == 1 ? " day" : " days").append(" late").append(t.required() ? "" : ", optional").append(")");
        }
        if (tasks.size() > shown) sb.append("; and ").append(tasks.size() - shown).append(" more");
        return sb.append(".").toString();
    }
}
