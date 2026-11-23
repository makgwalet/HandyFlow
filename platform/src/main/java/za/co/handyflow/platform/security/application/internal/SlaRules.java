package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service-level targets for incidents and how an incident stands against them. Pure: no clock of its own and no database.
 * <p>
 * The clock starts when the incident was recorded (not when the guard says it happened, which can be backdated). Acknowledge and resolve are
 * judged separately; the incident's overall state is the worst of the two. An incident resolved without anyone having acknowledged it counts as
 * acknowledged when it was resolved.
 */
public final class SlaRules {

    public static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 43200; // 30 days
    /** A window is "at risk" for its last fifth. */
    static final double AT_RISK_FRACTION = 0.2;

    public enum State { MET, BREACHED, AT_RISK, PENDING, UNTRACKED }

    public record Target(int acknowledgeMinutes, int resolveMinutes) {}

    public record Stage(State state, Instant deadline, Long takenMinutes, Long overdueMinutes, Long remainingMinutes) {}

    public record Evaluation(State overall, Stage acknowledge, Stage resolve) {
        public static final Evaluation UNTRACKED = new Evaluation(State.UNTRACKED, null, null);
    }

    private SlaRules() {}

    // ---- targets ----

    public static String severity(String s) {
        String v = s == null ? "" : s.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SEVERITIES.contains(v)) throw new HandyFlowException("Severity must be LOW, MEDIUM, HIGH or CRITICAL.", HttpStatus.BAD_REQUEST, "SLA_SEVERITY");
        return v;
    }

    public static Target validate(Integer acknowledgeMinutes, Integer resolveMinutes) {
        if (acknowledgeMinutes == null || resolveMinutes == null || acknowledgeMinutes < MIN_MINUTES || resolveMinutes < MIN_MINUTES
                || acknowledgeMinutes > MAX_MINUTES || resolveMinutes > MAX_MINUTES) {
            throw new HandyFlowException("Minutes must be between " + MIN_MINUTES + " and " + MAX_MINUTES + " (30 days).", HttpStatus.BAD_REQUEST, "SLA_MINUTES");
        }
        if (resolveMinutes < acknowledgeMinutes) {
            throw new HandyFlowException("The time to resolve cannot be shorter than the time to acknowledge.", HttpStatus.BAD_REQUEST, "SLA_ORDER");
        }
        return new Target(acknowledgeMinutes, resolveMinutes);
    }

    /** A site's own target beats the company default; with neither, the incident is not tracked (null). */
    public static Target pick(Target site, Target company) { return site != null ? site : company; }

    // ---- one incident ----

    /**
     * @param start  when the incident was recorded
     * @param done   when this stage was completed, or null while it is not
     * @param limit  the allowed minutes
     */
    public static Stage stage(Instant start, Instant done, int limit, Instant now) {
        Instant deadline = start.plus(Duration.ofMinutes(limit));
        if (done != null) {
            long taken = Math.max(0, Duration.between(start, done).toMinutes());
            if (!done.isAfter(deadline)) return new Stage(State.MET, deadline, taken, null, null);
            return new Stage(State.BREACHED, deadline, taken, Duration.between(deadline, done).toMinutes(), null);
        }
        if (now.isAfter(deadline)) return new Stage(State.BREACHED, deadline, null, Duration.between(deadline, now).toMinutes(), null);
        long remaining = Duration.between(now, deadline).toMinutes();
        boolean atRisk = Duration.between(now, deadline).toSeconds() <= Math.max(60L, Math.round(limit * 60L * AT_RISK_FRACTION));
        return new Stage(atRisk ? State.AT_RISK : State.PENDING, deadline, null, null, remaining);
    }

    public static Evaluation evaluate(Instant created, Instant acknowledged, Instant resolved, Target target, Instant now) {
        if (target == null || created == null) return Evaluation.UNTRACKED;
        // Resolved with no acknowledgement on record: the resolution is the acknowledgement.
        Instant ackDone = acknowledged != null ? (resolved != null && resolved.isBefore(acknowledged) ? resolved : acknowledged) : resolved;
        Stage ack = stage(created, ackDone, target.acknowledgeMinutes(), now);
        Stage res = stage(created, resolved, target.resolveMinutes(), now);
        return new Evaluation(worst(ack.state(), res.state()), ack, res);
    }

    static State worst(State a, State b) {
        return rank(a) >= rank(b) ? a : b;
    }

    static int rank(State s) {
        return switch (s) { case BREACHED -> 4; case AT_RISK -> 3; case PENDING -> 2; case MET -> 1; case UNTRACKED -> 0; };
    }

    // ---- many incidents ----

    public record Item(UUID incidentId, UUID siteId, String siteName, String severity, Evaluation evaluation) {}

    public record Row(UUID siteId, String siteName, String severity, int total, int tracked, int met, int breached, int atRisk, int pending,
                      Double compliancePct, Double averageAcknowledgeMinutes, Double averageResolveMinutes) {}

    /** Counts per site and severity, and for everything. Compliance is met out of met plus breached; null when there are none of either. */
    public static List<Row> summarise(List<Item> items) {
        Map<String, List<Item>> groups = new LinkedHashMap<>();
        for (Item i : items.stream().sorted(Comparator.comparing((Item x) -> x.siteName() == null ? "" : x.siteName().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(x -> SEVERITIES.indexOf(x.severity()))).toList()) {
            groups.computeIfAbsent(i.siteId() + "|" + i.severity(), k -> new ArrayList<>()).add(i);
        }
        List<Row> out = new ArrayList<>();
        for (List<Item> g : groups.values()) out.add(row(g.get(0).siteId(), g.get(0).siteName(), g.get(0).severity(), g));
        return out;
    }

    public static Row total(List<Item> items) { return row(null, null, null, items); }

    private static Row row(UUID siteId, String siteName, String severity, List<Item> g) {
        int tracked = 0, met = 0, breached = 0, risk = 0, pending = 0;
        double ackSum = 0, resSum = 0; int ackN = 0, resN = 0;
        for (Item i : g) {
            Evaluation e = i.evaluation();
            if (e.overall() == State.UNTRACKED) continue;
            tracked++;
            switch (e.overall()) { case MET -> met++; case BREACHED -> breached++; case AT_RISK -> risk++; case PENDING -> pending++; default -> { } }
            if (e.acknowledge().takenMinutes() != null) { ackSum += e.acknowledge().takenMinutes(); ackN++; }
            if (e.resolve().takenMinutes() != null) { resSum += e.resolve().takenMinutes(); resN++; }
        }
        Double pct = met + breached == 0 ? null : Math.round(met * 1000.0 / (met + breached)) / 10.0;
        return new Row(siteId, siteName, severity, g.size(), tracked, met, breached, risk, pending, pct,
                ackN == 0 ? null : Math.round(ackSum / ackN * 10) / 10.0, resN == 0 ? null : Math.round(resSum / resN * 10) / 10.0);
    }

    /** The incidents that need someone now: breached first (longest overdue first), then at risk (soonest due first). */
    public static List<Item> needingAttention(List<Item> items) {
        return items.stream().filter(i -> i.evaluation().overall() == State.BREACHED || i.evaluation().overall() == State.AT_RISK)
                .sorted(Comparator.comparingInt((Item i) -> i.evaluation().overall() == State.BREACHED ? 0 : 1)
                        .thenComparing(Comparator.comparingLong((Item i) -> -overdue(i.evaluation())))
                        .thenComparingLong(i -> remaining(i.evaluation())))
                .toList();
    }

    static long overdue(Evaluation e) {
        long a = e.acknowledge().overdueMinutes() == null ? 0 : e.acknowledge().overdueMinutes();
        long r = e.resolve().overdueMinutes() == null ? 0 : e.resolve().overdueMinutes();
        return Math.max(a, r);
    }

    static long remaining(Evaluation e) {
        long a = e.acknowledge().remainingMinutes() == null ? Long.MAX_VALUE : e.acknowledge().remainingMinutes();
        long r = e.resolve().remainingMinutes() == null ? Long.MAX_VALUE : e.resolve().remainingMinutes();
        return Math.min(a, r);
    }
}
