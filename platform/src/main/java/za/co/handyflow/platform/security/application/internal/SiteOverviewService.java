// security/application/internal/SiteOverviewService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.dto.LiveGuardResponse;
import za.co.handyflow.platform.security.dto.SiteOverviewDtos.*;
import za.co.handyflow.platform.security.dto.SiteResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One read model for the site detail page: guards on site now, next shifts, recent incidents, checkpoint scan counts. */
@Service
@RequiredArgsConstructor
public class SiteOverviewService {

    private final SiteService siteService;
    private final LiveOperationsService liveService;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Overview overview(TenantId tenantId, UUID siteId) { return overview(tenantId, siteId, Instant.now()); }

    @Transactional(readOnly = true)
    Overview overview(TenantId tenantId, UUID siteId, Instant now) {
        SiteResponse site = siteService.getSite(tenantId, siteId); // tenant-scoped; throws when it is not this tenant's site
        UUID tenant = tenantId.getValue();
        List<LiveGuardResponse> onSite = liveService.liveGuards(tenantId, siteId, now);

        List<UpcomingShift> upcoming = jdbc.query("""
                SELECT s.id, s.guard_id, g.first_name, g.last_name, s.start_at, s.end_at
                FROM security_shifts s JOIN security_guards g ON g.id = s.guard_id AND g.tenant_id = s.tenant_id
                WHERE s.tenant_id = ? AND s.site_id = ? AND s.status = 'SCHEDULED' AND s.deleted_at IS NULL
                  AND s.start_at >= ? AND s.start_at < ?
                ORDER BY s.start_at LIMIT 10""",
                (rs, n) -> new UpcomingShift((UUID) rs.getObject("id"), (UUID) rs.getObject("guard_id"),
                        (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                        rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant()),
                tenant, siteId, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofDays(7))));

        Integer upcomingCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM security_shifts WHERE tenant_id = ? AND site_id = ? AND status = 'SCHEDULED'
                  AND deleted_at IS NULL AND start_at >= ? AND start_at < ?""",
                Integer.class, tenant, siteId, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofDays(7))));

        List<IncidentRow> incidents = jdbc.query("""
                SELECT id, title, severity, status, created_at FROM security_incidents
                WHERE tenant_id = ? AND site_id = ? ORDER BY created_at DESC LIMIT 5""",
                (rs, n) -> new IncidentRow((UUID) rs.getObject("id"), rs.getString("title"), rs.getString("severity"),
                        rs.getString("status"), rs.getTimestamp("created_at").toInstant()),
                tenant, siteId);

        Integer openIncidents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM security_incidents WHERE tenant_id = ? AND site_id = ? AND status <> 'RESOLVED'""",
                Integer.class, tenant, siteId);

        List<CheckpointRow> checkpoints = jdbc.query("""
                SELECT c.id, c.name, c.sort_order,
                       COUNT(cl.id) FILTER (WHERE cl.scanned_at >= ?) AS scans30,
                       MAX(cl.scanned_at) AS last_scan
                FROM security_checkpoints c
                LEFT JOIN security_checkpoint_logs cl ON cl.checkpoint_id = c.id AND cl.tenant_id = c.tenant_id
                WHERE c.tenant_id = ? AND c.site_id = ? AND c.active = TRUE
                GROUP BY c.id, c.name, c.sort_order ORDER BY c.sort_order, c.name""",
                (rs, n) -> {
                    Timestamp last = rs.getTimestamp("last_scan");
                    return new CheckpointRow((UUID) rs.getObject("id"), rs.getString("name"), rs.getInt("scans30"),
                            last == null ? null : last.toInstant());
                },
                Timestamp.from(now.minus(Duration.ofDays(30))), tenant, siteId);

        Integer routes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM security_patrol_routes WHERE tenant_id = ? AND site_id = ? AND active = TRUE""",
                Integer.class, tenant, siteId);

        Counts counts = new Counts(onSite.size(), nz(upcomingCount), nz(openIncidents), nz(routes), checkpoints.size());
        return new Overview(site, counts, onSite, incidents, checkpoints, upcoming);
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
}
