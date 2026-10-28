package za.co.handyflow.platform.internalaudit.application.internal;

/** When an overdue item gets a reminder, and when it is escalated. Reminders are on the first day late and then weekly, so nobody is nagged daily. */
public final class ReminderRules {
    private ReminderRules() {}

    public enum Stage { NONE, REMIND, ESCALATE }
    public static final int ESCALATE_AFTER_DAYS = 14;
    /** Days between reminders once they start, and the day an unanswered review comment gets its first one. */
    public static final int REPEAT_EVERY_DAYS = 7, FIRST_REVIEW_REMINDER_DAY = 3;

    public static Stage stageFor(long daysOverdue) {
        if (daysOverdue < 1) return Stage.NONE;
        if (daysOverdue >= ESCALATE_AFTER_DAYS && daysOverdue % REPEAT_EVERY_DAYS == 0) return Stage.ESCALATE;
        if (daysOverdue == 1 || daysOverdue % REPEAT_EVERY_DAYS == 0) return Stage.REMIND;
        return Stage.NONE;
    }

    /** For a factual-review comment nobody has answered: a reminder on the third working-ish day, then weekly. */
    public static boolean reviewReminderDue(long daysWaiting) {
        return daysWaiting == FIRST_REVIEW_REMINDER_DAY || (daysWaiting >= REPEAT_EVERY_DAYS && daysWaiting % REPEAT_EVERY_DAYS == 0);
    }
}
