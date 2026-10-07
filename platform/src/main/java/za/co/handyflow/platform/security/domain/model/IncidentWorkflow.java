// security/domain/model/IncidentWorkflow.java
package za.co.handyflow.platform.security.domain.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What can be done to an incident, given its status and severity. Pure, so it can be tested directly.
 * Lifecycle: OPEN, ACKNOWLEDGED, RESOLVED. A resolved incident can be reopened (with a reason) or annotated, nothing else.
 */
public final class IncidentWorkflow {

    private IncidentWorkflow() {}

    public enum Action { ACKNOWLEDGE, RESOLVE, ASSIGN, ESCALATE, NOTE, EVIDENCE, REOPEN }

    public static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static Set<Action> allowed(String status, String severity) {
        boolean canEscalate = nextSeverity(severity) != null;
        return switch (status == null ? "" : status.toUpperCase()) {
            case "OPEN" -> withEscalate(EnumSet.of(Action.ACKNOWLEDGE, Action.RESOLVE, Action.ASSIGN, Action.NOTE, Action.EVIDENCE), canEscalate);
            case "ACKNOWLEDGED" -> withEscalate(EnumSet.of(Action.RESOLVE, Action.ASSIGN, Action.NOTE, Action.EVIDENCE), canEscalate);
            case "RESOLVED" -> EnumSet.of(Action.REOPEN, Action.NOTE);
            default -> EnumSet.noneOf(Action.class);
        };
    }

    private static Set<Action> withEscalate(Set<Action> s, boolean yes) { if (yes) s.add(Action.ESCALATE); return s; }

    /** The next level up, or null when already CRITICAL (or unknown). */
    public static String nextSeverity(String severity) {
        int i = severity == null ? -1 : SEVERITIES.indexOf(severity.toUpperCase());
        return i < 0 || i >= SEVERITIES.size() - 1 ? null : SEVERITIES.get(i + 1);
    }

    /** Null when raising `current` to `target` is fine, otherwise what is wrong. A null target means the next level. */
    public static String escalationError(String current, String target) {
        if (nextSeverity(current) == null) return "This incident is already at the highest severity";
        if (target == null || target.isBlank()) return null;
        int c = SEVERITIES.indexOf(current.toUpperCase()), t = SEVERITIES.indexOf(target.toUpperCase());
        if (t < 0) return "Unknown severity: " + target;
        if (t <= c) return "Escalating must raise the severity above " + current.toLowerCase();
        return null;
    }
}
