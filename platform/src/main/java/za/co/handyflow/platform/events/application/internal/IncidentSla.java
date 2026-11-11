package za.co.handyflow.platform.events.application.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * How long an incident has been waiting, and whether that is too long for its severity.
 * <p>
 * This is COMPUTED when an incident is read, not pushed: the Events module can't reach the
 * notifications module, so nothing can ring a bell when an incident goes overdue. The flags
 * therefore have to be impossible to miss on screen — and cheap, which is why this is pure
 * arithmetic over timestamps already on the row.
 * <p>
 * Targets (minutes) — acknowledge: someone has taken it; resolve: it is dealt with:
 * <pre>
 * CRITICAL   5 / 60      HIGH   15 / 180      MEDIUM   60 / 480      LOW   240 / 1440
 * </pre>
 * A resolved or closed incident is never overdue, however long it took — overdue means "still
 * needs attention", not "was slow".
 */
final class IncidentSla {

    record Assessment(boolean acknowledgementOverdue, boolean resolutionOverdue, long minutesOpen) {}

    private IncidentSla() {}

    static int acknowledgeWithinMinutes(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 15;
            case "MEDIUM" -> 60;
            default -> 240;
        };
    }

    static int resolveWithinMinutes(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 60;
            case "HIGH" -> 180;
            case "MEDIUM" -> 480;
            default -> 1440;
        };
    }

    static Assessment assess(String severity, String status, Instant createdAt,
                             Instant acknowledgedAt, Instant resolvedAt, Instant now) {
        boolean finished = "RESOLVED".equals(status) || "CLOSED".equals(status);
        if (finished) {
            long took = resolvedAt == null ? 0 : Math.max(0, Duration.between(createdAt, resolvedAt).toMinutes());
            return new Assessment(false, false, took);
        }
        long open = Math.max(0, Duration.between(createdAt, now).toMinutes());
        boolean ackOverdue = acknowledgedAt == null && open > acknowledgeWithinMinutes(severity);
        boolean resolveOverdue = open > resolveWithinMinutes(severity);
        return new Assessment(ackOverdue, resolveOverdue, open);
    }
}