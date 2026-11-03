package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.enums.ProjectHealth;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;
import za.co.handyflow.platform.projects.dto.PortfolioResponse;
import za.co.handyflow.platform.projects.dto.PortfolioResponse.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard figures across the projects the caller can see (member-scoped when access control is on), with last month's
 * closing snapshot for deltas and up to six months of trend. Cost totals only include projects whose cost the caller may see.
 */
@Service
@RequiredArgsConstructor
public class ProjectPortfolioService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int MILESTONE_WINDOW_DAYS = 14;

    private final ProjectRepository projects;
    private final ProjectTaskRepository tasks;
    private final ProjectAccessGuard access;
    private final JdbcTemplate jdbc;

    /** The rule used by the dashboard and the snapshot job alike. */
    static boolean overdue(ProjectStatus status, LocalDate endDate, LocalDate today) {
        return (status == ProjectStatus.APPROVED || status == ProjectStatus.ACTIVE || status == ProjectStatus.ON_HOLD)
                && endDate != null && endDate.isBefore(today);
    }

    /** One row of figures for a project: what the counters and sums are built from. */
    record Row(UUID id, String status, String health, boolean overdue, BigDecimal budget, BigDecimal spent, BigDecimal committed) {}

    static Counts count(Collection<Row> rows, Set<UUID> costVisible) {
        int total = 0, active = 0, onTrack = 0, atRisk = 0, overdue = 0, onHold = 0, green = 0, amber = 0, red = 0;
        BigDecimal budget = BigDecimal.ZERO, spent = BigDecimal.ZERO, committed = BigDecimal.ZERO;
        for (Row r : rows) {
            if ("CANCELLED".equals(r.status())) continue;
            total++;
            boolean live = "ACTIVE".equals(r.status()) || "ON_HOLD".equals(r.status());
            if ("ACTIVE".equals(r.status())) active++;
            if ("ON_HOLD".equals(r.status())) onHold++;
            if (r.overdue()) overdue++;
            if (live) {
                switch (r.health()) {
                    case "RED" -> red++;
                    case "AMBER" -> amber++;
                    default -> green++;
                }
                if (!r.overdue() && "GREEN".equals(r.health()) && "ACTIVE".equals(r.status())) onTrack++;
                else if (!r.overdue()) atRisk++;
            }
            if (costVisible.contains(r.id()) && !"CLOSED".equals(r.status())) {
                budget = budget.add(nz(r.budget())); spent = spent.add(nz(r.spent())); committed = committed.add(nz(r.committed()));
            }
        }
        return new Counts(total, active, onTrack, atRisk, overdue, onHold, green, amber, red, budget, spent, committed);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse portfolio(TenantId tenant) {
        UUID tid = tenant.getValue();
        LocalDate today = LocalDate.now(SAST);
        UUID memberUser = access.memberScopeUserId();
        List<Project> visible = (memberUser != null
                ? projects.findActiveForMember(tid, memberUser, Pageable.unpaged())
                : projects.findActive(tid, Pageable.unpaged())).getContent();
        Map<UUID, Project> byId = visible.stream().collect(Collectors.toMap(Project::getId, p -> p));
        Set<UUID> costVisible = access.accessFor(tid, byId.keySet()).entrySet().stream()
                .filter(e -> e.getValue().cost()).map(Map.Entry::getKey).collect(Collectors.toSet());

        List<Row> live = visible.stream().map(p -> new Row(p.getId(), p.getStatus().name(), p.getHealth().name(),
                overdue(p.getStatus(), p.getEndDate(), today), p.getBudgetTotal(), p.getBudgetSpent(), p.getBudgetCommitted())).toList();
        Counts now = count(live, costVisible);
        int costCount = (int) live.stream().filter(r -> costVisible.contains(r.id()) && !"CANCELLED".equals(r.status()) && !"CLOSED".equals(r.status())).count();
        int hidden = (int) live.stream().filter(r -> !costVisible.contains(r.id()) && !"CANCELLED".equals(r.status()) && !"CLOSED".equals(r.status())).count();

        // month-end snapshots of these projects: last month for the deltas, six months for the trend
        LocalDate thisMonth = today.withDayOfMonth(1);
        LocalDate from = thisMonth.minusMonths(6);
        Map<LocalDate, List<Row>> byMonth = new TreeMap<>();
        jdbc.query("""
                SELECT project_id, month, status, health, overdue, budget_total, budget_spent, budget_committed
                  FROM project_monthly_snapshots
                 WHERE tenant_id = ? AND month >= ? AND month < ?
                """, rs -> {
            UUID pid = (UUID) rs.getObject("project_id");
            if (!byId.containsKey(pid)) return;
            byMonth.computeIfAbsent(rs.getDate("month").toLocalDate(), m -> new ArrayList<>()).add(new Row(pid, rs.getString("status"),
                    rs.getString("health"), rs.getBoolean("overdue"), rs.getBigDecimal("budget_total"), rs.getBigDecimal("budget_spent"), rs.getBigDecimal("budget_committed")));
        }, tid, Date.valueOf(from), Date.valueOf(thisMonth));

        LocalDate lastMonth = thisMonth.minusMonths(1);
        Counts previous = byMonth.containsKey(lastMonth) ? count(byMonth.get(lastMonth), costVisible) : null;
        List<TrendPoint> trend = byMonth.entrySet().stream().map(e -> {
            Counts c = count(e.getValue(), costVisible);
            return new TrendPoint(e.getKey(), c.onTrack(), c.atRisk(), c.overdue(), c.spent());
        }).toList();

        List<Milestone> milestones = byId.isEmpty() ? List.of() : tasks.findMilestonesDue(tid, byId.keySet(), today, today.plusDays(MILESTONE_WINDOW_DAYS))
                .stream().limit(10).map(t -> {
                    Project p = byId.get(t.getProjectId());
                    return new Milestone(t.getId(), p.getId(), p.getProjectNumber(), p.getName(), t.getTaskNumber(), t.getTitle(),
                            t.getPlannedEnd(), (int) ChronoUnit.DAYS.between(today, t.getPlannedEnd()));
                }).toList();

        return new PortfolioResponse(today, now, previous, previous == null ? null : lastMonth, costCount, hidden, milestones, trend);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
