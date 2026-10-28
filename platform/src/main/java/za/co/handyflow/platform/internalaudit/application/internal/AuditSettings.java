package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.ArrayList;
import java.util.List;

/** The rules internal audit runs by for one company, and what is allowed to be changed. Pure; the Audit Settings screen reads and writes these. */
public final class AuditSettings {
    private AuditSettings() {}

    public record Values(int lowMax, int mediumMax, int highMax, int escalateAfterDays, int repeatEveryDays, int firstReviewReminderDay) {}
    /** The rules a company has until it changes them. */
    public static final Values DEFAULTS = new Values(10, 15, 20, 14, 7, 3);

    public static final int SCORE_MIN = 5, SCORE_MAX = 25;

    public record Band(String level, int from, int to) {}
    public record Role(String key, String label) {}
    public record Settings(
            int scoreMin, int scoreMax, List<Band> riskBands, List<String> riskInputs,
            int escalateAfterDays, int firstReviewReminderDay, int repeatEveryDays, List<Role> engagementRoles,
            Values values, Values defaults, boolean custom) {}

    private static final List<String> ROLES = List.of("HEAD_OF_INTERNAL_AUDIT", "AUDIT_MANAGER", "SENIOR_AUDITOR", "AUDITOR", "AUDIT_REVIEWER");

    /** What is wrong with these values, in words the person can act on; empty when they are fine. */
    public static List<String> problems(Values v) {
        List<String> p = new ArrayList<>();
        if (v.lowMax() < SCORE_MIN) p.add("Low must end at " + SCORE_MIN + " or higher");
        if (v.mediumMax() <= v.lowMax()) p.add("Medium must end above where Low ends");
        if (v.highMax() <= v.mediumMax()) p.add("High must end above where Medium ends");
        if (v.highMax() > SCORE_MAX - 1) p.add("High must end at " + (SCORE_MAX - 1) + " or lower, so Critical has at least one score");
        if (v.repeatEveryDays() < 1 || v.repeatEveryDays() > 30) p.add("Repeat reminders every 1 to 30 days");
        if (v.escalateAfterDays() < 2 || v.escalateAfterDays() > 90) p.add("Escalate after 2 to 90 days");
        if (v.firstReviewReminderDay() < 1 || v.firstReviewReminderDay() > 30) p.add("Remind about review comments after 1 to 30 days");
        return p;
    }

    public static Settings of(Values v, boolean custom) {
        return new Settings(SCORE_MIN, SCORE_MAX,
                List.of(new Band("LOW", SCORE_MIN, v.lowMax()), new Band("MEDIUM", v.lowMax() + 1, v.mediumMax()),
                        new Band("HIGH", v.mediumMax() + 1, v.highMax()), new Band("CRITICAL", v.highMax() + 1, SCORE_MAX)),
                List.of("Inherent risk", "Control risk", "Past findings", "Time since last audit", "Business impact"),
                v.escalateAfterDays(), v.firstReviewReminderDay(), v.repeatEveryDays(),
                ROLES.stream().map(r -> new Role(r, label(r))).toList(), v, DEFAULTS, custom);
    }

    public static Settings current() { return of(DEFAULTS, false); }

    static String label(String key) {
        StringBuilder b = new StringBuilder();
        for (String w : key.toLowerCase().split("_")) b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
}
