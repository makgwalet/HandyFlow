package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Metrics;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Snapshot;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The saved headline figures, one row a day (V549). */
@Component
@RequiredArgsConstructor
public class AuditSnapshotStore {
    private final JdbcTemplate jdbc;

    /** Saves today's figures, replacing any already saved for today (the last look of the day wins). */
    public void save(UUID tenant, LocalDate day, Metrics m) {
        jdbc.update("""
                INSERT INTO audit_dashboard_snapshots (tenant_id, taken_on, engagements, open_findings, overdue_actions, high_risk_areas)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, taken_on) DO UPDATE SET engagements = EXCLUDED.engagements, open_findings = EXCLUDED.open_findings,
                    overdue_actions = EXCLUDED.overdue_actions, high_risk_areas = EXCLUDED.high_risk_areas
                """, tenant, Date.valueOf(day), m.engagements(), m.openFindings(), m.overdueActions(), m.highRiskAreas());
    }

    /** The saved days before `before`, newest first, at most `limit`. */
    public List<Snapshot> history(UUID tenant, LocalDate before, int limit) {
        return jdbc.query("""
                SELECT taken_on, engagements, open_findings, overdue_actions, high_risk_areas
                FROM audit_dashboard_snapshots WHERE tenant_id = ? AND taken_on < ? ORDER BY taken_on DESC LIMIT ?
                """, (rs, i) -> new Snapshot(rs.getDate(1).toLocalDate(), new Metrics(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5))),
                tenant, Date.valueOf(before), limit);
    }
}
