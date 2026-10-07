// security/application/internal/GuardScoreHistoryStore.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and writes the daily score snapshots (security_guard_score_history). Written by the nightly job only. */
@Component
@RequiredArgsConstructor
public class GuardScoreHistoryStore {

    /** One day's snapshot. `recommendations` is "CODE:LEVEL,CODE:LEVEL", empty when there were none. */
    public record Snapshot(LocalDate date, Integer score, String band, int coverage, String recommendations) {}

    private final JdbcTemplate jdbc;

    /** The recommendations on the most recent snapshot before `date`, or empty when the guard has no earlier snapshot. */
    public Optional<String> previousRecommendations(TenantId tenantId, UUID guardId, LocalDate date) {
        List<String> rows = jdbc.query("""
                SELECT recommendations FROM security_guard_score_history
                WHERE tenant_id = ? AND guard_id = ? AND snapshot_date < ?
                ORDER BY snapshot_date DESC LIMIT 1
                """, (rs, i) -> rs.getString(1), tenantId.getValue(), guardId, date);
        return rows.stream().findFirst();
    }

    /** Writes the day's snapshot, replacing one already taken for the same day. */
    public void save(TenantId tenantId, UUID guardId, LocalDate date, Integer score, String band, int coverage, String recommendations) {
        jdbc.update("""
                INSERT INTO security_guard_score_history (id, tenant_id, guard_id, snapshot_date, score, band, coverage, recommendations)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, guard_id, snapshot_date)
                DO UPDATE SET score = EXCLUDED.score, band = EXCLUDED.band, coverage = EXCLUDED.coverage,
                              recommendations = EXCLUDED.recommendations
                """, UUID.randomUUID(), tenantId.getValue(), guardId, date, score, band, coverage, recommendations);
    }

    /** The newest `limit` snapshots for a guard, oldest first so they can be drawn left to right. */
    public List<Snapshot> history(TenantId tenantId, UUID guardId, int limit) {
        List<Snapshot> rows = jdbc.query("""
                SELECT snapshot_date, score, band, coverage, recommendations FROM security_guard_score_history
                WHERE tenant_id = ? AND guard_id = ? ORDER BY snapshot_date DESC LIMIT ?
                """, (rs, i) -> new Snapshot(rs.getDate(1).toLocalDate(), (Integer) rs.getObject(2), rs.getString(3), rs.getInt(4), rs.getString(5)),
                tenantId.getValue(), guardId, limit);
        return rows.reversed();
    }
}
