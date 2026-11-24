package za.co.handyflow.platform.contracting.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How long approvals take, measured on the company's waiting clock (see {@link ApprovalWorkingTime}), and how many needed a reminder or an
 * escalation. Pure arithmetic over plain samples so it can be tested without a database.
 */
public final class ApprovalSla {

    private ApprovalSla() {}

    /** One approval request as the report sees it. {@code decidedAt} is empty until someone decides it. */
    public record Sample(UUID id, String action, String status, UUID requestedBy, Instant requestedAt, Instant decidedAt, UUID decidedBy,
                         int escalationLevel, boolean soleUserException) {
        boolean decided() { return decidedAt != null && ("APPROVED".equals(status) || "REJECTED".equals(status) || "CONSUMED".equals(status)); }
        boolean waiting() { return "PENDING".equals(status); }
    }

    public record Group(String key, long decided, Long averageMinutes, long escalated) {}

    public record Overdue(UUID id, String action, long waitedMinutes, int escalationLevel) {}

    public record Result(long requested, long decided, long waiting, long cancelledOrOutOfDate,
                         Long averageMinutes, Long medianMinutes, Long ninetiethPercentileMinutes, Double decidedWithinTargetPercent,
                         long reminded, long escalated, long soleUserExceptions, long waitingPastTarget,
                         List<Group> byAction, List<Group> byApprover, List<Overdue> overdue) {}

    /**
     * @param targetMinutes the time an approval should be decided within (the escalation threshold)
     */
    public static Result compute(List<Sample> samples, ApprovalWorkingTime clock, Instant now, long targetMinutes) {
        List<Long> decidedMinutes = new ArrayList<>();
        long waiting = 0, other = 0, reminded = 0, escalated = 0, sole = 0, withinTarget = 0, pastTarget = 0;
        Map<String, List<Long>> action = new LinkedHashMap<>();
        Map<String, Long> actionEscalated = new LinkedHashMap<>();
        Map<String, List<Long>> approver = new LinkedHashMap<>();
        Map<String, Long> approverEscalated = new LinkedHashMap<>();
        List<Overdue> overdue = new ArrayList<>();

        for (Sample s : samples) {
            if (s.escalationLevel() >= 1) reminded++;
            if (s.escalationLevel() >= 3) escalated++;
            if (s.soleUserException()) sole++;
            if (s.decided()) {
                long m = clock.minutesBetween(s.requestedAt(), s.decidedAt());
                decidedMinutes.add(m);
                if (m <= targetMinutes) withinTarget++;
                action.computeIfAbsent(s.action(), k -> new ArrayList<>()).add(m);
                if (s.escalationLevel() >= 3) actionEscalated.merge(s.action(), 1L, Long::sum);
                if (s.decidedBy() != null) {
                    approver.computeIfAbsent(s.decidedBy().toString(), k -> new ArrayList<>()).add(m);
                    if (s.escalationLevel() >= 3) approverEscalated.merge(s.decidedBy().toString(), 1L, Long::sum);
                }
            } else if (s.waiting()) {
                waiting++;
                long waited = clock.minutesBetween(s.requestedAt(), now);
                if (waited >= targetMinutes) {
                    pastTarget++;
                    overdue.add(new Overdue(s.id(), s.action(), waited, s.escalationLevel()));
                }
            } else {
                other++;
            }
        }
        overdue.sort(Comparator.comparingLong(Overdue::waitedMinutes).reversed());
        List<Long> sorted = decidedMinutes.stream().sorted().toList();
        Double pct = sorted.isEmpty() ? null : Math.round(withinTarget * 1000.0 / sorted.size()) / 10.0;
        return new Result(samples.size(), sorted.size(), waiting, other,
                average(sorted), percentile(sorted, 50), percentile(sorted, 90), pct,
                reminded, escalated, sole, pastTarget,
                groups(action, actionEscalated), groups(approver, approverEscalated), overdue);
    }

    private static List<Group> groups(Map<String, List<Long>> values, Map<String, Long> escalated) {
        List<Group> out = new ArrayList<>();
        for (var e : values.entrySet()) {
            List<Long> v = e.getValue();
            out.add(new Group(e.getKey(), v.size(), average(v), escalated.getOrDefault(e.getKey(), 0L)));
        }
        out.sort(Comparator.comparingLong(Group::decided).reversed().thenComparing(Group::key));
        return out;
    }

    private static Long average(List<Long> v) {
        if (v.isEmpty()) return null;
        long sum = 0;
        for (long x : v) sum += x;
        return Math.round((double) sum / v.size());
    }

    /** Nearest-rank percentile of an already sorted list. */
    static Long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return null;
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }
}
