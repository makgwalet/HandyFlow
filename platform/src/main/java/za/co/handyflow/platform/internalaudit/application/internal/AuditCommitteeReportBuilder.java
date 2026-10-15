package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Works out the audit committee report from plain inputs so the rules can be tested without a database. */
public final class AuditCommitteeReportBuilder {
    private AuditCommitteeReportBuilder() {}

    public record UniverseIn(UUID id, String name, String risk) {}
    public record PlanEntryIn(UUID universeEntryId, Integer quarter, String status) {}
    public record FindingIn(UUID id, String title, String severity, String status, LocalDate dueDate) {}
    public record ActionIn(UUID id, UUID findingId, String description, LocalDate dueDate, String ownerName) {}
    public record ReportIn(String engagement, String engagementStatus, String approvalStatus, java.time.Instant approvedAt) {}
    public record ControlFigures(int open, int unrated, int highOrCritical, int overdue, int followUpsDue, int reopenedEver) {}

    public record QuarterRow(int quarter, int planned, int completed) {}
    public record PlanDelivery(Integer year, String status, int committed, int completed, int deferred, int completionPct, List<QuarterRow> quarters) {}
    public record Reports(int approved, int awaitingSignOff, int closedWithoutApproval, List<ApprovedReport> recentlyApproved) {}
    public record ApprovedReport(String engagement, java.time.Instant approvedAt) {}
    public record RiskRow(String level, int count) {}
    public record RiskProfile(List<RiskRow> byLevel, List<String> highRiskNotYetAudited) {}
    public record SeriousFindings(int openHighOrCritical, int pastDue) {}
    public record LateAction(UUID id, String description, String finding, String severity, String owner, LocalDate dueDate, long daysOverdue) {}
    public record Report(LocalDate asOf, PlanDelivery plan, Reports reports, RiskProfile risk, SeriousFindings findings,
                         int overdueHighRiskActionCount, List<LateAction> overdueHighRiskActions, ControlFigures controls) {}

    static final int LIST_LIMIT = 10;
    private static final List<String> LEVELS = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNASSESSED");
    private static boolean serious(String s) { return s != null && (s.equalsIgnoreCase("HIGH") || s.equalsIgnoreCase("CRITICAL")); }
    private static boolean open(String s) { return "OPEN".equals(s) || "IN_PROGRESS".equals(s); }
    private static int pct(int a, int b) { return b == 0 ? 0 : Math.round(a * 100f / b); }

    public static Report build(LocalDate today, Integer year, String planStatus, List<PlanEntryIn> entries, List<UniverseIn> universe,
                               List<FindingIn> findings, List<ActionIn> overdueActions, List<ReportIn> reports, ControlFigures controls) {
        int done = 0, deferred = 0;
        SortedMap<Integer, int[]> q = new TreeMap<>();
        for (PlanEntryIn e : entries) {
            if ("DEFERRED".equals(e.status())) { deferred++; continue; }
            boolean c = "COMPLETED".equals(e.status());
            if (c) done++;
            if (e.quarter() != null) { int[] r = q.computeIfAbsent(e.quarter(), k -> new int[2]); r[0]++; if (c) r[1]++; }
        }
        int committed = entries.size() - deferred;
        PlanDelivery plan = new PlanDelivery(year, planStatus, committed, done, deferred, pct(done, committed),
                q.entrySet().stream().map(e -> new QuarterRow(e.getKey(), e.getValue()[0], e.getValue()[1])).toList());

        int approved = 0, awaiting = 0, closedNo = 0;
        List<ApprovedReport> recent = new ArrayList<>();
        for (ReportIn r : reports) {
            String a = r.approvalStatus();
            if ("APPROVED".equals(a)) { approved++; recent.add(new ApprovedReport(r.engagement(), r.approvedAt())); }
            else if ("SUBMITTED".equals(a) || "IN_PROGRESS".equals(a) || "RESUBMITTED".equals(a)) awaiting++;
            if ("CLOSED".equals(r.engagementStatus()) && !"APPROVED".equals(a)) closedNo++;
        }
        recent.sort(Comparator.comparing(ApprovedReport::approvedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        Reports rep = new Reports(approved, awaiting, closedNo, recent.size() > LIST_LIMIT ? recent.subList(0, LIST_LIMIT) : recent);

        Set<UUID> audited = new HashSet<>();
        entries.forEach(e -> { if ("COMPLETED".equals(e.status())) audited.add(e.universeEntryId()); });
        Map<String, Integer> counts = new LinkedHashMap<>();
        LEVELS.forEach(l -> counts.put(l, 0));
        List<String> notAudited = new ArrayList<>();
        for (UniverseIn u : universe) {
            String level = u.risk() == null ? "UNASSESSED" : u.risk().toUpperCase();
            counts.merge(level, 1, Integer::sum);
            if (serious(level) && !audited.contains(u.id())) notAudited.add(u.name());
        }
        Collections.sort(notAudited);
        RiskProfile risk = new RiskProfile(counts.entrySet().stream().filter(e -> e.getValue() > 0).map(e -> new RiskRow(e.getKey(), e.getValue())).toList(),
                notAudited.size() > LIST_LIMIT ? notAudited.subList(0, LIST_LIMIT) : notAudited);

        int openSerious = 0, past = 0;
        Map<UUID, FindingIn> byId = new HashMap<>();
        for (FindingIn f : findings) {
            byId.put(f.id(), f);
            if (open(f.status()) && serious(f.severity())) { openSerious++; if (f.dueDate() != null && f.dueDate().isBefore(today)) past++; }
        }
        List<LateAction> late = new ArrayList<>();
        for (ActionIn a : overdueActions) {
            FindingIn f = byId.get(a.findingId());
            if (f == null || !serious(f.severity()) || a.dueDate() == null || !a.dueDate().isBefore(today)) continue;
            late.add(new LateAction(a.id(), a.description(), f.title(), f.severity().toUpperCase(), a.ownerName(), a.dueDate(), ChronoUnit.DAYS.between(a.dueDate(), today)));
        }
        late.sort(Comparator.comparing(LateAction::dueDate));
        return new Report(today, plan, rep, risk, new SeriousFindings(openSerious, past), late.size(),
                late.size() > LIST_LIMIT ? late.subList(0, LIST_LIMIT) : late, controls);
    }
}
