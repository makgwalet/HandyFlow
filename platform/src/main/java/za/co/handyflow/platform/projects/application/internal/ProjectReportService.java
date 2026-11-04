package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.cost.CostEngine;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectRisk;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRiskRepository;
import za.co.handyflow.platform.projects.dto.ReportResponse;
import za.co.handyflow.platform.projects.dto.ReportResponse.*;
import za.co.handyflow.platform.projects.dto.RiskHeatmapResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Portfolio reports and the risk heat map, over the projects the caller can see. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectReportService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ProjectRepository projects;
    private final ProjectRiskRepository risks;
    private final ProjectAccessGuard access;
    private final ProjectCostService costs;

    /** What the report needs to know about one project, so the arithmetic can be tested without a database. */
    record Facts(Project p, boolean costVisible, BigDecimal actual, BigDecimal forecast) {}

    @Transactional(readOnly = true)
    public ReportResponse report(TenantId tenant, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(SAST);
        LocalDate f = from != null ? from : today.withDayOfYear(1);
        LocalDate t = to != null ? to : today;
        if (t.isBefore(f)) throw new IllegalArgumentException("The end of the period is before its start");
        if (ChronoUnit.DAYS.between(f, t) > 3700) throw new IllegalArgumentException("Choose a period of ten years or less");

        List<Project> visible = visible(tenant);
        Map<UUID, ProjectAccess> acc = access.accessFor(tenant.getValue(), visible.stream().map(Project::getId).toList());
        List<Facts> facts = new ArrayList<>();
        for (Project p : visible) {
            boolean cost = acc.getOrDefault(p.getId(), ProjectAccess.NONE).cost();
            BigDecimal actual = nz(p.getBudgetSpent()), forecast = null;
            if (cost && p.getStatus() != ProjectStatus.CANCELLED) {
                try {
                    CostEngine.Summary s = costs.summary(tenant, p.getId());
                    actual = nz(s.actual()); forecast = s.eac();
                } catch (RuntimeException ex) {
                    log.warn("[PM] Cost summary failed for project {}: {}", p.getId(), ex.getMessage());
                }
            }
            facts.add(new Facts(p, cost, actual, forecast));
        }
        return build(facts, f, t, today);
    }

    static ReportResponse build(List<Facts> facts, LocalDate from, LocalDate to, LocalDate today) {
        List<ProjectRow> rows = new ArrayList<>();
        int completed = 0, onTime = 0, liveOverdue = 0, liveLate = 0, overBudget = 0, hidden = 0;
        long varianceSum = 0; int varianceN = 0;
        BigDecimal revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO, liveRevenue = BigDecimal.ZERO, liveCost = BigDecimal.ZERO, overrun = BigDecimal.ZERO;

        for (Facts x : facts) {
            Project p = x.p();
            if (p.getStatus() == ProjectStatus.CANCELLED) continue;
            LocalDate completedOn = p.getCompletedAt() == null ? null : p.getCompletedAt().atZone(SAST).toLocalDate();
            boolean done = (p.getStatus() == ProjectStatus.COMPLETED || p.getStatus() == ProjectStatus.CLOSED) && completedOn != null;
            boolean inPeriod = done && !completedOn.isBefore(from) && !completedOn.isAfter(to);
            boolean live = p.getStatus() == ProjectStatus.ACTIVE || p.getStatus() == ProjectStatus.ON_HOLD || p.getStatus() == ProjectStatus.APPROVED;
            LocalDate target = p.getBaselineEnd() != null ? p.getBaselineEnd() : p.getEndDate();

            Boolean rowOnTime = null; Integer variance = null;
            if (done && target != null) {
                variance = (int) ChronoUnit.DAYS.between(target, completedOn);
                rowOnTime = variance <= 0;
            }
            if (inPeriod) {
                completed++;
                if (Boolean.TRUE.equals(rowOnTime)) onTime++;
                if (variance != null) { varianceSum += variance; varianceN++; }
            }
            if (live && p.getEndDate() != null && p.getEndDate().isBefore(today)) liveOverdue++;
            if (live && p.getBaselineEnd() != null && p.getEndDate() != null && p.getEndDate().isAfter(p.getBaselineEnd())) liveLate++;

            BigDecimal contract = p.getContractValue(), margin = null; Integer marginPct = null; boolean forecastMargin = false;
            if (!x.costVisible()) hidden++;
            else {
                BigDecimal budget = nz(p.getBudgetTotal());
                BigDecimal finalCost = done ? x.actual() : (x.forecast() != null ? x.forecast() : x.actual().max(budget));
                if (budget.signum() > 0 && finalCost.compareTo(budget) > 0) { overBudget++; overrun = overrun.add(finalCost.subtract(budget)); }
                if (contract != null) {
                    margin = contract.subtract(finalCost);
                    marginPct = pct(margin, contract);
                    forecastMargin = !done;
                    if (inPeriod) { revenue = revenue.add(contract); cost = cost.add(x.actual()); }
                    if (live) { liveRevenue = liveRevenue.add(contract); liveCost = liveCost.add(finalCost); }
                }
            }
            rows.add(new ProjectRow(p.getId(), p.getProjectNumber(), p.getName(), p.getClientName(), p.getProjectType(), p.getStatus().name(),
                    p.getHealth().name(), p.getStartDate(), p.getEndDate(), p.getBaselineEnd(), completedOn, rowOnTime, variance, x.costVisible(),
                    x.costVisible() ? contract : null, x.costVisible() ? p.getBudgetTotal() : null, x.costVisible() ? x.actual() : null,
                    x.costVisible() ? x.forecast() : null, margin, marginPct, forecastMargin));
        }
        BigDecimal margin = revenue.subtract(cost), liveMargin = liveRevenue.subtract(liveCost);
        Delivery delivery = new Delivery(completed, onTime, completed == 0 ? null : (int) Math.round(100.0 * onTime / completed),
                varianceN == 0 ? null : (int) Math.round((double) varianceSum / varianceN), liveOverdue, liveLate);
        Money money = new Money(revenue, cost, margin, pct(margin, revenue), liveRevenue, liveCost, liveMargin, pct(liveMargin, liveRevenue), overrun, overBudget);
        rows.sort(Comparator.comparing(ProjectRow::projectNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        return new ReportResponse(from, to, today, delivery, money, rows,
                group(rows, r -> r.clientName() == null ? "No client" : r.clientName(), from, to),
                group(rows, r -> r.projectType() == null ? "Other" : r.projectType(), from, to), hidden);
    }

    /** Totals per client or type: margin only for completed projects in the period, so groups add up to the headline. */
    static List<GroupRow> group(List<ProjectRow> rows, Function<ProjectRow, String> key, LocalDate from, LocalDate to) {
        Map<String, List<ProjectRow>> by = rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
        List<GroupRow> out = new ArrayList<>();
        by.forEach((k, list) -> {
            int completed = 0, onTime = 0;
            BigDecimal revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO;
            for (ProjectRow r : list) {
                boolean inPeriod = r.completedOn() != null && !r.completedOn().isBefore(from) && !r.completedOn().isAfter(to);
                if (!inPeriod) continue;
                completed++;
                if (Boolean.TRUE.equals(r.onTime())) onTime++;
                if (r.costVisible() && r.contractValue() != null) { revenue = revenue.add(r.contractValue()); cost = cost.add(nz(r.actual())); }
            }
            BigDecimal margin = revenue.subtract(cost);
            out.add(new GroupRow(k, list.size(), completed, onTime, revenue, cost, margin, pct(margin, revenue)));
        });
        out.sort(Comparator.comparing(GroupRow::revenue).reversed().thenComparing(GroupRow::key));
        return out;
    }

    @Transactional(readOnly = true)
    public RiskHeatmapResponse heatmap(TenantId tenant, UUID projectId) {
        List<Project> scope = projectId == null ? visible(tenant)
                : projects.findByTenantAndId(tenant.getValue(), projectId).map(List::of).orElse(List.of());
        Map<UUID, Project> byId = scope.stream().collect(Collectors.toMap(Project::getId, p -> p));
        List<ProjectRisk> open = new ArrayList<>();
        for (Project p : scope) {
            if (p.getStatus() == ProjectStatus.CANCELLED || p.getStatus() == ProjectStatus.CLOSED) continue;
            risks.findByProject(p.getId()).stream().filter(r -> "OPEN".equals(r.getStatus())).forEach(open::add);
        }
        return heatmapOf(open, byId);
    }

    static RiskHeatmapResponse heatmapOf(List<ProjectRisk> open, Map<UUID, Project> byId) {
        int[][] cells = new int[5][5];
        for (ProjectRisk r : open) cells[clamp(r.getImpact()) - 1][clamp(r.getProbability()) - 1]++;
        List<RiskHeatmapResponse.TopRisk> top = open.stream()
                .sorted(Comparator.comparingInt((ProjectRisk r) -> r.getProbability() * r.getImpact()).reversed().thenComparing(ProjectRisk::getTitle))
                .limit(10).map(r -> {
                    Project p = byId.get(r.getProjectId());
                    return new RiskHeatmapResponse.TopRisk(r.getId(), r.getProjectId(), p == null ? null : p.getProjectNumber(), p == null ? null : p.getName(),
                            r.getRiskNumber(), r.getTitle(), r.getProbability(), r.getImpact(), r.getRating(), r.getOwnerName());
                }).toList();
        return new RiskHeatmapResponse(cells, open.size(), top);
    }

    private List<Project> visible(TenantId tenant) {
        UUID memberUser = access.memberScopeUserId();
        return (memberUser != null ? projects.findActiveForMember(tenant.getValue(), memberUser, Pageable.unpaged())
                : projects.findActive(tenant.getValue(), Pageable.unpaged())).getContent();
    }

    static Integer pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0 || part == null) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).intValue();
    }
    private static int clamp(int v) { return Math.max(1, Math.min(5, v)); }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
