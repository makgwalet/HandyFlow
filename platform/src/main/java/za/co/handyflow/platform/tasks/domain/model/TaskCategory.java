package za.co.handyflow.platform.tasks.domain.model;

/**
 * Workflow stage of a board column. A task's status is always the category of the column it
 * sits in, so nothing has to guess a status from a column's NAME.
 */
public enum TaskCategory {
    TODO, IN_PROGRESS, IN_REVIEW, BLOCKED, DONE;

    /** Parses a client-supplied value. Null or blank means "not supplied". */
    public static TaskCategory parseOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        return TaskCategory.valueOf(value.trim().toUpperCase());
    }
}
