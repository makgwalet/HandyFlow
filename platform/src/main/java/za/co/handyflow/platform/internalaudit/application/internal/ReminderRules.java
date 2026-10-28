package za.co.handyflow.platform.internalaudit.application.internal;

/** When an overdue item gets a reminder, and when it is escalated. Reminders are on the first day late and then at a regular interval, so nobody is nagged daily. */
public final class ReminderRules {
    private ReminderRules() {}

    public enum Stage { NONE, REMIND, ESCALATE }
    public static final int ESCALATE_AFTER_DAYS = 14;
    /** The standard days between reminders once they start, and the day an unanswered review comment gets its first one. */
    public static final int REPEAT_EVERY_DAYS = 7, FIRST_REVIEW_REMINDER_DAY = 3;

    public static Stage stageFor(long daysOverdue) { return stageFor(daysOverdue, ESCALATE_AFTER_DAYS, REPEAT_EVERY_DAYS); }

    /** Escalation happens on the first reminder day on or after {@code escalateAfter}. */
    public static Stage stageFor(long daysOverdue, int escalateAfter, int repeatEvery) {
        if (daysOverdue < 1) return Stage.NONE;
        if (daysOverdue >= escalateAfter && daysOverdue % repeatEvery == 0) return Stage.ESCALATE;
        if (daysOverdue == 1 || daysOverdue % repeatEvery == 0) return Stage.REMIND;
        return Stage.NONE;
    }

    /** For a factual-review comment nobody has answered: a reminder on the first day, then at the repeat interval. */
    public static boolean reviewReminderDue(long daysWaiting) { return reviewReminderDue(daysWaiting, FIRST_REVIEW_REMINDER_DAY, REPEAT_EVERY_DAYS); }

    public static boolean reviewReminderDue(long daysWaiting, int firstDay, int repeatEvery) {
        return daysWaiting == firstDay || (daysWaiting > firstDay && daysWaiting % repeatEvery == 0);
    }
}
