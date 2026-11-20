package za.co.handyflow.platform.legalpractice.application.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** The rules for matter tasks and workflow templates, with no database. */
public final class TaskRules {

    private TaskRules() { }

    public static final int MIN_REASON = 10;
    public static final int MAX_STEPS = 30;
    public static final int MAX_OFFSET_DAYS = 730;
    public static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    public static final Set<String> ASSIGN_TO = Set.of("STARTER", "NONE");

    public static boolean live(String status) { return "OPEN".equals(status) || "IN_PROGRESS".equals(status); }

    public static boolean overdue(String status, LocalDate due, LocalDate today) { return live(status) && due != null && due.isBefore(today); }

    public static String reasonProblem(String reason, String what) {
        return reason == null || reason.trim().length() < MIN_REASON ? "Say " + what + ", in at least " + MIN_REASON + " characters" : null;
    }

    /** Free text that would not fit its column is refused here, with a message, rather than failing in the database. */
    public static String lengthProblem(String text, int max, String what) {
        return text != null && text.trim().length() > max ? "Keep " + what + " to " + max + " characters" : null;
    }

    public static String taskProblem(String title, String description, String priority) {
        String p = taskProblem(title, priority);
        return p != null ? p : lengthProblem(description, 2000, "the description");
    }

    public static String taskProblem(String title, String priority) {
        if (title == null || title.trim().length() < 2) return "Give the task a title";
        if (title.trim().length() > 200) return "Keep the title to 200 characters";
        if (priority == null || !PRIORITIES.contains(priority)) return "Choose a priority";
        return null;
    }

    /** The statuses a task can move to from where it is. A cancelled task is final; a finished one can only be reopened. */
    public static List<String> nextStatuses(String status) {
        return switch (status) {
            case "OPEN" -> List.of("IN_PROGRESS", "DONE", "CANCELLED");
            case "IN_PROGRESS" -> List.of("OPEN", "DONE", "CANCELLED");
            case "DONE" -> List.of("OPEN");
            default -> List.of();
        };
    }

    public static String moveProblem(String from, String to) {
        if (!nextStatuses(from).contains(to)) return "A task that is " + from.toLowerCase().replace('_', ' ') + " cannot be moved to " + to.toLowerCase().replace('_', ' ');
        return null;
    }

    public static String templateProblem(String name) {
        if (name == null || name.trim().length() < 2) return "Give the workflow a name";
        if (name.trim().length() > 120) return "Keep the name to 120 characters";
        return null;
    }

    public static String templateProblem(String name, String description, String matterType) {
        String p = templateProblem(name);
        if (p == null) p = lengthProblem(description, 1000, "the description");
        return p != null ? p : lengthProblem(matterType, 50, "the matter type");
    }

    public record Step(String title, String description, String priority, int offsetDays, String assignTo) { }

    public static String stepsProblem(List<Step> steps) {
        if (steps == null || steps.isEmpty()) return "Add at least one step";
        if (steps.size() > MAX_STEPS) return "A workflow can have up to " + MAX_STEPS + " steps";
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String n = "Step " + (i + 1) + ": ";
            if (s.title() == null || s.title().trim().length() < 2) return n + "give it a title";
            if (s.title().trim().length() > 200) return n + "keep the title to 200 characters";
            if (s.description() != null && s.description().trim().length() > 2000) return n + "keep the description to 2000 characters";
            if (s.priority() == null || !PRIORITIES.contains(s.priority())) return n + "choose a priority";
            if (s.offsetDays() < 0 || s.offsetDays() > MAX_OFFSET_DAYS) return n + "the due date must be between 0 and " + MAX_OFFSET_DAYS + " days after the start";
            if (s.assignTo() == null || !ASSIGN_TO.contains(s.assignTo())) return n + "choose who it is assigned to";
        }
        return null;
    }

    public static String applyProblem(LocalDate start, LocalDate today) {
        if (start == null) return "Enter the date the workflow starts";
        if (start.isBefore(today.minusDays(365)) || start.isAfter(today.plusDays(365))) return "Choose a start date within a year of today";
        return null;
    }

    public static LocalDate dueFor(LocalDate start, int offsetDays) { return start.plusDays(offsetDays); }
}
