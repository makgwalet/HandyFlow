package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Findings across every engagement with how far management has got in putting each right, worked out from plain inputs so the arithmetic can
 * be tested without a database. A finding's remediation is its action plans: an action is done once the auditor has validated it; one that is
 * only completed is waiting for validation; a cancelled action is left out of the count.
 */
public final class RemediationBoard {
    private RemediationBoard() {}

    public record FindingIn(UUID id, UUID engagementId, String title, String description, String recommendation, String managementResponse,
                            String severity, String status, UUID ownerId, LocalDate dueDate, Instant createdAt, boolean repeat) {}
    public record EngagementIn(UUID id, String name, String area) {}
    public record ActionIn(UUID id, UUID findingId, String description, UUID ownerId, LocalDate dueDate, String status) {}

    public record Row(UUID id, UUID engagementId, String engagement, String area, String title, String description, String recommendation,
                      String managementResponse, String severity, String status, String owner, LocalDate dueDate, boolean pastDue, boolean repeat,
                      int actions, int actionsDone, int actionsAwaiting, int actionsOverdue, int remediationPct, Instant createdAt) {}
    public record ActionRow(UUID id, UUID findingId, String finding, String severity, String engagement, String description, String owner,
                            LocalDate dueDate, String status, boolean overdue, long daysOverdue) {}
    public record Summary(int findings, int open, int inProgress, int resolved, int closed, int openCritical, int openHigh, int openMedium,
                          int openLow, int pastDue, int openWithoutAction, int actions, int actionsOpen, int actionsAwaiting,
                          int actionsValidated, int actionsOverdue, int remediationPct) {}
    public record Board(Summary summary, List<Row> findings, List<ActionRow> actions) {}

    static boolean isOpen(String s) { return "OPEN".equals(s) || "IN_PROGRESS".equals(s); }
    static boolean live(ActionIn a) { return !"CANCELLED".equals(a.status()); }
    static boolean overdue(ActionIn a, LocalDate today) { return ("OPEN".equals(a.status()) || "COMPLETED".equals(a.status())) && a.dueDate() != null && a.dueDate().isBefore(today); }
    static int pct(int part, int whole) { return whole == 0 ? 0 : Math.round(part * 100f / whole); }

    private static final Map<String, Integer> SEVERITY_RANK = Map.of("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3);

    public static Board build(List<FindingIn> findings, List<EngagementIn> engagements, List<ActionIn> actions, Map<UUID, String> names, LocalDate today) {
        Map<UUID, EngagementIn> eng = new HashMap<>();
        engagements.forEach(e -> eng.put(e.id(), e));
        Map<UUID, List<ActionIn>> byFinding = new HashMap<>();
        actions.forEach(a -> byFinding.computeIfAbsent(a.findingId(), k -> new ArrayList<>()).add(a));

        List<Row> rows = new ArrayList<>();
        int open = 0, inProgress = 0, resolved = 0, closed = 0, critical = 0, high = 0, medium = 0, low = 0, pastDue = 0, withoutAction = 0;
        for (FindingIn f : findings) {
            List<ActionIn> mine = byFinding.getOrDefault(f.id(), List.of()).stream().filter(RemediationBoard::live).toList();
            int done = (int) mine.stream().filter(a -> "VALIDATED".equals(a.status())).count();
            int awaiting = (int) mine.stream().filter(a -> "COMPLETED".equals(a.status())).count();
            int late = (int) mine.stream().filter(a -> overdue(a, today)).count();
            boolean late1 = isOpen(f.status()) && f.dueDate() != null && f.dueDate().isBefore(today);
            switch (f.status() == null ? "" : f.status()) {
                case "OPEN" -> open++;
                case "IN_PROGRESS" -> inProgress++;
                case "RESOLVED" -> resolved++;
                case "CLOSED" -> closed++;
                default -> { }
            }
            if (isOpen(f.status())) {
                switch (f.severity() == null ? "" : f.severity().toUpperCase()) {
                    case "CRITICAL" -> critical++;
                    case "HIGH" -> high++;
                    case "MEDIUM" -> medium++;
                    case "LOW" -> low++;
                    default -> { }
                }
                if (mine.isEmpty()) withoutAction++;
            }
            if (late1) pastDue++;
            EngagementIn e = eng.get(f.engagementId());
            rows.add(new Row(f.id(), f.engagementId(), e == null ? null : e.name(), e == null ? null : e.area(), f.title(), f.description(),
                    f.recommendation(), f.managementResponse(), f.severity(), f.status(), f.ownerId() == null ? null : names.get(f.ownerId()),
                    f.dueDate(), late1, f.repeat(), mine.size(), done, awaiting, late, pct(done, mine.size()), f.createdAt()));
        }
        // Worst first: open before settled, then by severity, then the earliest due date, then newest.
        rows.sort(Comparator.<Row>comparingInt(r -> isOpen(r.status()) ? 0 : 1)
                .thenComparingInt(r -> SEVERITY_RANK.getOrDefault(r.severity() == null ? "" : r.severity().toUpperCase(), 4))
                .thenComparing(Row::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Row::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<UUID, FindingIn> fById = new HashMap<>();
        findings.forEach(f -> fById.put(f.id(), f));
        List<ActionRow> actionRows = new ArrayList<>();
        int aOpen = 0, aAwaiting = 0, aValidated = 0, aOverdue = 0, aLive = 0;
        for (ActionIn a : actions) {
            FindingIn f = fById.get(a.findingId());
            if (f == null) continue;
            if (live(a)) aLive++;
            switch (a.status() == null ? "" : a.status()) {
                case "OPEN" -> aOpen++;
                case "COMPLETED" -> aAwaiting++;
                case "VALIDATED" -> aValidated++;
                default -> { }
            }
            boolean od = overdue(a, today);
            if (od) aOverdue++;
            EngagementIn e = eng.get(f.engagementId());
            actionRows.add(new ActionRow(a.id(), f.id(), f.title(), f.severity(), e == null ? null : e.name(), a.description(),
                    a.ownerId() == null ? null : names.get(a.ownerId()), a.dueDate(), a.status(), od,
                    od ? ChronoUnit.DAYS.between(a.dueDate(), today) : 0));
        }
        // Overdue first (the longest late first), then those still to be done, then the settled ones; each by due date.
        actionRows.sort(Comparator.<ActionRow>comparingInt(r -> r.overdue() ? 0 : "OPEN".equals(r.status()) || "COMPLETED".equals(r.status()) ? 1 : 2)
                .thenComparing(Comparator.comparingLong(ActionRow::daysOverdue).reversed())
                .thenComparing(ActionRow::dueDate, Comparator.nullsLast(Comparator.naturalOrder())));

        Summary s = new Summary(findings.size(), open, inProgress, resolved, closed, critical, high, medium, low, pastDue, withoutAction,
                aLive, aOpen, aAwaiting, aValidated, aOverdue, pct(aValidated, aLive));
        return new Board(s, rows, actionRows);
    }
}
