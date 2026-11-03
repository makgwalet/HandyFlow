package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;

/**
 * Writes this month's snapshot row for every project (all tenants), nightly at 23:50 SAST and once at start-up, so the last
 * write of each month is its closing figure. Uses the same overdue rule as {@link ProjectPortfolioService#overdue}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSnapshotJob {

    static final String UPSERT = """
            INSERT INTO project_monthly_snapshots
                   (tenant_id, project_id, month, status, health, overdue, budget_total, budget_spent, budget_committed, completion_pct, taken_at)
            SELECT p.tenant_id, p.id,
                   date_trunc('month', (now() AT TIME ZONE 'Africa/Johannesburg'))::date,
                   p.status, p.health,
                   (p.status IN ('APPROVED','ACTIVE','ON_HOLD') AND p.end_date IS NOT NULL
                        AND p.end_date < (now() AT TIME ZONE 'Africa/Johannesburg')::date),
                   COALESCE(p.budget_total, 0), COALESCE(p.budget_spent, 0), COALESCE(p.budget_committed, 0),
                   COALESCE((SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE t.status = 'COMPLETED')
                                     / NULLIF(COUNT(*) FILTER (WHERE t.status <> 'CANCELLED'), 0), 2)
                               FROM project_tasks t WHERE t.project_id = p.id), 0),
                   now()
              FROM projects p
             WHERE p.status <> 'CANCELLED'
            ON CONFLICT (project_id, month) DO UPDATE SET
                   status = EXCLUDED.status, health = EXCLUDED.health, overdue = EXCLUDED.overdue,
                   budget_total = EXCLUDED.budget_total, budget_spent = EXCLUDED.budget_spent,
                   budget_committed = EXCLUDED.budget_committed, completion_pct = EXCLUDED.completion_pct,
                   taken_at = EXCLUDED.taken_at
            """;

    private final JdbcTemplate jdbc;
    private final SchedulerLease lease;

    @Scheduled(cron = "0 50 23 * * *", zone = "Africa/Johannesburg")
    public void nightly() { run(); }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        try { run(); } catch (RuntimeException e) { log.warn("[PM] Project snapshot at start-up failed: {}", e.getMessage()); }
    }

    void run() {
        lease.runExclusively("pm-monthly-snapshot", Duration.ofMinutes(10), () -> {
            int rows = jdbc.update(UPSERT);
            log.info("[PM] Monthly project snapshot written for {} project(s)", rows);
        });
    }
}
