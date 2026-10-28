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
import java.util.UUID;

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
        return DashboardTrends.compare(now, history, today);
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
