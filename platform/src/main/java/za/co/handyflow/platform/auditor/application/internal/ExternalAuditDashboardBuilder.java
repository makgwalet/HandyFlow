package za.co.handyflow.platform.auditor.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Works out the external audit dashboard from plain inputs so the counting rules can be tested without a database. */
public final class ExternalAuditDashboardBuilder {
    private ExternalAuditDashboardBuilder() {}

    public record EngagementIn(UUID id, String name, String status) {}
    public record RequestIn(UUID engagementId, String reference, String title, String status, LocalDate dueDate, int timesRejected) {}
    public record QueryIn(UUID engagementId, String kind, String reference, String title, String status, LocalDate dueDate, int timesReopened) {}

    public record RequestFigures(int awaitingBusiness, int overdue, int dueSoon, int awaitingAuditor, int rejectedBefore, int approved, int notAvailable) {}
    public record QueryFigures(int awaitingBusiness, int overdue, int awaitingAuditor, int reopenedBefore, int resolved) {}
    public record NoteFigures(int unresolved, int overdue) {}
    public record EngagementRow(UUID engagementId, String name, String status, int openRequests, int overdueRequests, int openQueries, int unresolvedNotes) {}
    public record OverdueItem(String type, String reference, String title, String engagement, LocalDate dueDate, long daysOverdue) {}
    public record Dashboard(RequestFigures requests, QueryFigures queries, NoteFigures reviewNotes, int overdueTotal,
                            List<EngagementRow> engagements, List<OverdueItem> overdueItems) {}

    static final int LIST_LIMIT = 10;
    private static boolean late(LocalDate due, LocalDate today) { return due != null && due.isBefore(today); }

    /** @param onlyEngagement when set, every figure is limited to that engagement. */
    public static Dashboard build(List<EngagementIn> engagements, List<RequestIn> requests, List<QueryIn> queries, UUID onlyEngagement, LocalDate today) {
        Map<UUID, EngagementIn> byId = new HashMap<>();
        engagements.forEach(e -> byId.put(e.id(), e));
        List<RequestIn> rs = requests.stream().filter(r -> onlyEngagement == null || onlyEngagement.equals(r.engagementId())).toList();
        List<QueryIn> all = queries.stream().filter(q -> onlyEngagement == null || onlyEngagement.equals(q.engagementId())).toList();
        List<QueryIn> qs = all.stream().filter(q -> "QUERY".equals(q.kind())).toList();
        List<QueryIn> ns = all.stream().filter(q -> "REVIEW_NOTE".equals(q.kind())).toList();

        int rOpen = 0, rOver = 0, rSoon = 0, rSub = 0, rRej = 0, rApp = 0, rNa = 0;
        for (RequestIn r : rs) {
            switch (r.status()) {
                case "OPEN" -> { rOpen++; if (late(r.dueDate(), today)) rOver++;
                    else if (r.dueDate() != null && !r.dueDate().isAfter(today.plusDays(3))) rSoon++; if (r.timesRejected() > 0) rRej++; }
                case "SUBMITTED" -> rSub++;
                case "APPROVED" -> rApp++;
                case "NOT_AVAILABLE", "CLOSED_NOT_AVAILABLE" -> rNa++;
                default -> {}
            }
        }
        int qOpen = 0, qOver = 0, qAns = 0, qRe = 0, qRes = 0;
        for (QueryIn q : qs) {
            switch (q.status()) {
                case "OPEN" -> { qOpen++; if (late(q.dueDate(), today)) qOver++; if (q.timesReopened() > 0) qRe++; }
                case "ANSWERED" -> qAns++;
                case "RESOLVED" -> qRes++;
                default -> {}
            }
        }
        int nOpen = 0, nOver = 0;
        for (QueryIn n : ns) {
            if ("OPEN".equals(n.status()) || "ANSWERED".equals(n.status())) { nOpen++; if ("OPEN".equals(n.status()) && late(n.dueDate(), today)) nOver++; }
        }

        List<EngagementRow> rows = new ArrayList<>();
        Set<UUID> ids = new LinkedHashSet<>();
        engagements.forEach(e -> { if (onlyEngagement == null || onlyEngagement.equals(e.id())) ids.add(e.id()); });
        rs.forEach(r -> ids.add(r.engagementId())); all.forEach(q -> ids.add(q.engagementId()));
        for (UUID id : ids) {
            EngagementIn e = byId.get(id);
            int or = (int) rs.stream().filter(r -> Objects.equals(r.engagementId(), id) && "OPEN".equals(r.status())).count();
            int ov = (int) rs.stream().filter(r -> Objects.equals(r.engagementId(), id) && "OPEN".equals(r.status()) && late(r.dueDate(), today)).count();
            int oq = (int) qs.stream().filter(q -> Objects.equals(q.engagementId(), id) && "OPEN".equals(q.status())).count();
            int un = (int) ns.stream().filter(q -> Objects.equals(q.engagementId(), id) && ("OPEN".equals(q.status()) || "ANSWERED".equals(q.status()))).count();
            rows.add(new EngagementRow(id, e == null ? "No engagement" : e.name(), e == null ? null : e.status(), or, ov, oq, un));
        }
        rows.sort(Comparator.comparingInt((EngagementRow r) -> -(r.overdueRequests() + r.openRequests() + r.openQueries() + r.unresolvedNotes())));

        List<OverdueItem> items = new ArrayList<>();
        java.util.function.Function<UUID, String> nameOf = id -> byId.get(id) == null ? "No engagement" : byId.get(id).name();
        for (RequestIn r : rs) if ("OPEN".equals(r.status()) && late(r.dueDate(), today))
            items.add(new OverdueItem("EVIDENCE_REQUEST", r.reference(), r.title(), nameOf.apply(r.engagementId()), r.dueDate(), ChronoUnit.DAYS.between(r.dueDate(), today)));
        for (QueryIn q : all) if ("OPEN".equals(q.status()) && late(q.dueDate(), today))
            items.add(new OverdueItem(q.kind(), q.reference(), q.title(), nameOf.apply(q.engagementId()), q.dueDate(), ChronoUnit.DAYS.between(q.dueDate(), today)));
        items.sort(Comparator.comparing(OverdueItem::dueDate));

        int overdueTotal = rOver + qOver + nOver;
        return new Dashboard(new RequestFigures(rOpen, rOver, rSoon, rSub, rRej, rApp, rNa), new QueryFigures(qOpen, qOver, qAns, qRe, qRes),
                new NoteFigures(nOpen, nOver), overdueTotal, rows, items.size() > LIST_LIMIT ? items.subList(0, LIST_LIMIT) : items);
    }
}
