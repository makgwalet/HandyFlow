package za.co.handyflow.platform.internalaudit.application.internal;

import za.co.handyflow.platform.internalaudit.domain.model.RiskAssessment;

import java.util.List;

/** The rules internal audit runs by, read from the same constants the code uses, so the Audit Settings screen cannot disagree with it. */
public final class AuditSettings {
    private AuditSettings() {}

    public record Band(String level, int from, int to) {}
    public record Role(String key, String label) {}
    public record Settings(
            int scoreMin, int scoreMax, List<Band> riskBands, List<String> riskInputs,
            int escalateAfterDays, int firstReviewReminderDay, int repeatEveryDays, List<Role> engagementRoles) {}

    private static final List<String> ROLES = List.of("HEAD_OF_INTERNAL_AUDIT", "AUDIT_MANAGER", "SENIOR_AUDITOR", "AUDITOR", "AUDIT_REVIEWER");

    public static Settings current() {
        return new Settings(5, 25,
                List.of(new Band("LOW", 5, RiskAssessment.LOW_MAX),
                        new Band("MEDIUM", RiskAssessment.LOW_MAX + 1, RiskAssessment.MEDIUM_MAX),
                        new Band("HIGH", RiskAssessment.MEDIUM_MAX + 1, RiskAssessment.HIGH_MAX),
                        new Band("CRITICAL", RiskAssessment.HIGH_MAX + 1, 25)),
                List.of("Inherent risk", "Control risk", "Past findings", "Time since last audit", "Business impact"),
                ReminderRules.ESCALATE_AFTER_DAYS, ReminderRules.FIRST_REVIEW_REMINDER_DAY, ReminderRules.REPEAT_EVERY_DAYS,
                ROLES.stream().map(r -> new Role(r, label(r))).toList());
    }

    static String label(String key) {
        StringBuilder b = new StringBuilder();
        for (String w : key.toLowerCase().split("_")) b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
}
