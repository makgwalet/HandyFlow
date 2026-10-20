package za.co.handyflow.platform.earthmoving.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Rules for alert routing. A company chooses who is told and how long to wait before the next level is told, inside
 * these limits. The limits are the minimum safety controls and are not configurable.
 */
public final class AlertRules {
    private AlertRules() {}

    public static final Set<String> TYPES = Set.of("SERVICE_DUE", "BREAKDOWN", "WORK_ORDER_OVERDUE", "DOCUMENT_EXPIRY", "DISPOSAL_APPROVAL");
    /** A breakdown must reach someone, and must reach the next level within this time if nobody acknowledges it. */
    public static final int BREAKDOWN_MAX_ESCALATION_MINUTES = 60;
    public static final int MAX_ESCALATION_MINUTES = 7 * 24 * 60;

    public static void checkRule(String type, boolean enabled, Integer escalateAfterMinutes,
                                 Collection<UUID> initial, Collection<UUID> escalation) {
        EarthmovingRules.oneOf(type, TYPES, "Alert type");
        if (escalateAfterMinutes != null && (escalateAfterMinutes < 0 || escalateAfterMinutes > MAX_ESCALATION_MINUTES))
            throw new IllegalArgumentException("Escalation time must be between 0 minutes and 7 days.");
        if (!escalation.isEmpty() && escalateAfterMinutes == null)
            throw new IllegalArgumentException("Say how many minutes to wait before the escalation recipients are told.");
        if ("BREAKDOWN".equals(type)) {
            if (!enabled) throw new IllegalArgumentException("Breakdown alerts cannot be switched off.");
            if (!escalation.isEmpty() && escalateAfterMinutes > BREAKDOWN_MAX_ESCALATION_MINUTES)
                throw new IllegalArgumentException("A breakdown must escalate within " + BREAKDOWN_MAX_ESCALATION_MINUTES + " minutes.");
        }
    }

    public static boolean isEscalationDue(Instant createdAt, Instant now, Integer escalateAfterMinutes) {
        return escalateAfterMinutes != null && !now.isBefore(createdAt.plus(Duration.ofMinutes(escalateAfterMinutes)));
    }

    public static String severityFor(String type, String incidentSeverity) {
        if ("BREAKDOWN".equals(type)) return "CRITICAL".equals(incidentSeverity) || "HIGH".equals(incidentSeverity) ? "CRITICAL" : "WARNING";
        return "WARNING";
    }
}
