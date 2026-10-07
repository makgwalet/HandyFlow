// security/application/internal/LiveOperationsService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.dto.LiveGuardResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live Operations: every guard on an ACTIVE shift, with the last GPS position (if any since the shift began) and the
 * last checkpoint scan, in one query. Replaces the screen assembling this from four calls and one call per shift.
 * Real data only: a guard with no ping is listed as NO_GPS, never given a made-up position.
 */
@Service
@RequiredArgsConstructor
public class LiveOperationsService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<LiveGuardResponse> liveGuards(TenantId tenantId, UUID siteId) { return liveGuards(tenantId, siteId, Instant.now()); }

    @Transactional(readOnly = true)
    List<LiveGuardResponse> liveGuards(TenantId tenantId, UUID siteId, Instant now) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id AS shift_id, s.site_id, ss.name AS site_name, s.guard_id,
                       g.first_name, g.last_name, g.grade, s.start_at, s.end_at,
                       l.latitude, l.longitude, l.recorded_at,
                       ls.scanned_at AS last_scan_at, ls.checkpoint_name
                FROM security_shifts s
                JOIN security_guards g ON g.id = s.guard_id AND g.tenant_id = s.tenant_id
                LEFT JOIN security_sites ss ON ss.id = s.site_id
                LEFT JOIN security_guard_current_location l ON l.guard_id = s.guard_id AND l.tenant_id = s.tenant_id
                LEFT JOIN LATERAL (
                    SELECT cl.scanned_at, c.name AS checkpoint_name
                    FROM security_checkpoint_logs cl
                    JOIN security_checkpoints c ON c.id = cl.checkpoint_id
                    WHERE cl.shift_id = s.id AND cl.tenant_id = s.tenant_id
                    ORDER BY cl.scanned_at DESC LIMIT 1
                ) ls ON TRUE
                WHERE s.tenant_id = ? AND s.status = 'ACTIVE' AND s.deleted_at IS NULL
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId.getValue());
        if (siteId != null) { sql.append(" AND s.site_id = ?"); params.add(siteId); }
        sql.append(" ORDER BY s.start_at, g.last_name, g.first_name");
        return jdbc.query(sql.toString(), (rs, n) -> map(rs, now), params.toArray());
    }

    static LiveGuardResponse map(ResultSet rs, Instant now) throws SQLException {
        Instant start = instant(rs.getTimestamp("start_at")), end = instant(rs.getTimestamp("end_at"));
        Instant recorded = instant(rs.getTimestamp("recorded_at"));
        String state = LiveStatus.gps(recorded, start, now);
        boolean hasPosition = !LiveStatus.NO_GPS.equals(state);
        return new LiveGuardResponse(
                (UUID) rs.getObject("guard_id"),
                (rs.getString("first_name") + " " + rs.getString("last_name")).trim(), rs.getString("grade"),
                (UUID) rs.getObject("shift_id"), start, end, LiveStatus.overrunning(end, now),
                (UUID) rs.getObject("site_id"), rs.getString("site_name"),
                hasPosition ? rs.getBigDecimal("latitude") : null, hasPosition ? rs.getBigDecimal("longitude") : null,
                hasPosition ? recorded : null, state,
                instant(rs.getTimestamp("last_scan_at")), rs.getString("checkpoint_name"));
    }

    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }
}
