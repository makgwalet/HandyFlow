package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Metrics;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Trends;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalAuditTrendService {
    /** Saved days looked at when choosing what to compare with: a little over a year. */
    static final int HISTORY_LIMIT = 400;

    private final AuditEngagementRepository engagements;
    private final AuditFindingRepository findings;
    private final AuditActionPlanRepository actions;
    private final AuditUniverseEntryRepository universe;
    private final RiskAssessmentRepository risks;
    private final AuditSnapshotStore store;

    /** Counts the figures now, saves them as today's, and compares them with the past. A failure to save is logged and does not stop the answer. */
    @Transactional
    public Trends trends(TenantId tenant, LocalDate today) {
        UUID t = tenant.getValue();
        Metrics now = current(t, today);
        var history = store.history(t, today, HISTORY_LIMIT);
        try { store.save(t, today, now); } catch (RuntimeException e) { log.warn("[InternalAudit] Could not save the dashboard snapshot: {}", e.getMessage()); }
        Trends saved = DashboardTrends.compare(now, history, today);
        if (saved.daysAgo() != null) return saved;
        try { return reconstructed(t, now, today); }
        catch (RuntimeException e) { log.warn("[InternalAudit] Could not work out earlier dashboard figures: {}", e.getMessage()); return saved; }
    }

    /**
     * Nothing saved is old enough, so work the earlier figures out from recorded dates. The comparison is with a quarter ago, or with the day the
     * company's oldest record was made if that is later; with records under a week old there is nothing to compare with yet.
     */
    Trends reconstructed(UUID t, Metrics now, LocalDate today) {
        var eng = engagements.findAllForTenant(t).stream().map(e -> new HistoricalMetrics.EngagementIn(e.getCreatedAt())).toList();
        var fin = findings.findAllForTenant(t).stream().map(f -> new HistoricalMetrics.FindingIn(f.getCreatedAt(), f.getResolvedAt(), f.getClosedAt(), f.getStatus())).toList();
        var act = actions.findAllForTenant(t).stream().map(a -> new HistoricalMetrics.ActionIn(a.getCreatedAt(), a.getDueDate(), a.getValidatedAt(), a.getStatus())).toList();
        Map<UUID, List<HistoricalMetrics.RatingIn>> ratings = risks.findAllForTenant(t).stream().collect(Collectors.groupingBy(
                r -> r.getUniverseEntryId(), Collectors.mapping(r -> new HistoricalMetrics.RatingIn(r.getAssessedAt(), r.getFinalAuditRisk()), Collectors.toList())));
        var areas = universe.findAllActive(t).stream().map(u -> new HistoricalMetrics.AreaIn(u.getCreatedAt(), ratings.getOrDefault(u.getId(), List.of()))).toList();

        LocalDate earliest = HistoricalMetrics.earliest(eng, fin, act, areas);
        if (earliest == null) return DashboardTrends.none();
        LocalDate base = today.minusDays(DashboardTrends.TARGET_AGE_DAYS);
        if (earliest.isAfter(base)) base = earliest;
        if (ChronoUnit.DAYS.between(base, today) < DashboardTrends.MIN_AGE_DAYS) return DashboardTrends.none();
        return DashboardTrends.against(now, base, HistoricalMetrics.asAt(base, eng, fin, act, areas), today, DashboardTrends.RECONSTRUCTED);
    }

    Metrics current(UUID t, LocalDate today) {
        int open = (int) findings.findAllForTenant(t).stream().filter(f -> InternalAuditDashboardBuilder.isOpen(f.getStatus())).count();
        int overdue = (int) actions.findOverdue(t, today).stream().filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today)).count();
        int high = (int) universe.findAllActive(t).stream()
                .map(u -> risks.findLatestForUniverseEntry(t, u.getId()).map(r -> r.getFinalAuditRisk()).orElse(null))
                .filter(r -> "HIGH".equals(r) || "CRITICAL".equals(r)).count();
        return new Metrics(engagements.findAllForTenant(t).size(), open, overdue, high);
    }
}
