package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Works out the internal audit dashboard figures from plain inputs, so the arithmetic can be tested without a database. */
public final class InternalAuditDashboardBuilder {
    private InternalAuditDashboardBuilder() {}

    public record UniverseIn(UUID id, String name) {}
    public record PlanEntryIn(UUID universeEntryId, String status) {}
    public record FindingIn(UUID id, String title, String severity, String status, LocalDate dueDate, boolean repeat) {}
    public record ActionIn(UUID id, UUID findingId, String description, LocalDate dueDate) {}

    public record PlanProgress(Integer year, String status, int total, int completed, int inProgress, int deferred, int planned, int completionPct) {}
    public record Coverage(int universeSize, int planned, int audited, int plannedPct, int auditedPct, List<String> notPlanned) {}
    public record SeverityRow(String severity, int open, int total) {}
    public record OverdueAction(UUID id, UUID findingId, String description, LocalDate dueDate, long daysOverdue) {}
    public record RepeatFinding(UUID id, String title, String severity, String status) {}
    public record Dashboard(PlanProgress plan, Coverage coverage, List<SeverityRow> findingsBySeverity, int openFindings,
                            int findingsPastDue, int overdueActionCount, List<OverdueAction> overdueActions,
                            int repeatFindingCount, List<RepeatFinding> repeatFindings) {}

    private static final List<String> ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNRATED");
    static final int LIST_LIMIT = 10;

    public static boolean isOpen(String status) { return "OPEN".equals(status) || "IN_PROGRESS".equals(status); }

    static int pct(int part, int whole) { return whole == 0 ? 0 : Math.round(part * 100f / whole); }

    public static Dashboard build(Integer planYear, String planStatus, List<PlanEntryIn> entries, List<UniverseIn> universe,
                                  List<FindingIn> findings, List<ActionIn> overdue, LocalDate today) {
        int completed = 0, inProgress = 0, deferred = 0, planned = 0;
        for (PlanEntryIn e : entries) {
            switch (e.status() == null ? "" : e.status()) {
                case "COMPLETED" -> completed++;
                case "IN_PROGRESS" -> inProgress++;
                case "DEFERRED" -> deferred++;
                default -> planned++;
            }
        }
        // A deferred entry is not part of what the year set out to do.
        int committed = entries.size() - deferred;
        PlanProgress plan = new PlanProgress(planYear, planStatus, entries.size(), completed, inProgress, deferred, planned, pct(completed, committed));

        Set<UUID> inPlan = new HashSet<>(), done = new HashSet<>();
        for (PlanEntryIn e : entries) {
            if (!"DEFERRED".equals(e.status())) inPlan.add(e.universeEntryId());
            if ("COMPLETED".equals(e.status())) done.add(e.universeEntryId());
        }
        List<String> notPlanned = universe.stream().filter(u -> !inPlan.contains(u.id())).map(UniverseIn::name).sorted().toList();
        int plannedCount = (int) universe.stream().filter(u -> inPlan.contains(u.id())).count();
        int auditedCount = (int) universe.stream().filter(u -> done.contains(u.id())).count();
        Coverage coverage = new Coverage(universe.size(), plannedCount, auditedCount, pct(plannedCount, universe.size()),
                pct(auditedCount, universe.size()), notPlanned.size() > LIST_LIMIT ? notPlanned.subList(0, LIST_LIMIT) : notPlanned);

        Map<String, int[]> bySev = new LinkedHashMap<>();
        ORDER.forEach(s -> bySev.put(s, new int[2]));
        int open = 0, pastDue = 0;
        for (FindingIn f : findings) {
            String sev = f.severity() == null ? "UNRATED" : f.severity().toUpperCase();
            int[] row = bySev.computeIfAbsent(sev, k -> new int[2]);
            row[1]++;
            if (isOpen(f.status())) {
                row[0]++; open++;
                if (f.dueDate() != null && f.dueDate().isBefore(today)) pastDue++;
            }
        }
        List<SeverityRow> sevRows = bySev.entrySet().stream().filter(e -> e.getValue()[1] > 0)
                .map(e -> new SeverityRow(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        List<OverdueAction> actions = overdue.stream()
                .filter(a -> a.dueDate() != null && a.dueDate().isBefore(today))
                .sorted(Comparator.comparing(ActionIn::dueDate))
                .map(a -> new OverdueAction(a.id(), a.findingId(), a.description(), a.dueDate(), ChronoUnit.DAYS.between(a.dueDate(), today))).toList();

        List<RepeatFinding> repeats = findings.stream().filter(FindingIn::repeat)
                .map(f -> new RepeatFinding(f.id(), f.title(), f.severity(), f.status())).toList();

        return new Dashboard(plan, coverage, sevRows, open, pastDue, actions.size(),
                actions.size() > LIST_LIMIT ? actions.subList(0, LIST_LIMIT) : actions,
                repeats.size(), repeats.size() > LIST_LIMIT ? repeats.subList(0, LIST_LIMIT) : repeats);
    }
}
