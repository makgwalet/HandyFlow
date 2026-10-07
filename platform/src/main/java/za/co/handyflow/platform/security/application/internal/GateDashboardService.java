// security/application/internal/GateDashboardService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.dto.GateDashboardDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is on site now across sites, how many came and went today, and which entries the overstay scheduler has flagged.
 * "Today" is the South African calendar day. Status OVERSTAYED comes from the existing overstay scheduler; nothing here
 * decides who has overstayed.
 */
@Service
@RequiredArgsConstructor
public class GateDashboardService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int ON_SITE_LIMIT = 200;

    private final JdbcTemplate jdbc;

    /** The instant today began in South African time. */
    static Instant todayStart(Instant now) {
        LocalDate d = now.atZone(SAST).toLocalDate();
        return d.atStartOfDay(SAST).toInstant();
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(TenantId tenantId, UUID siteId) { return dashboard(tenantId, siteId, Instant.now()); }

    @Transactional(readOnly = true)
    Dashboard dashboard(TenantId tenantId, UUID siteId, Instant now) {
        Instant today = todayStart(now);
        UUID tenant = tenantId.getValue();
        String siteFilter = siteId == null ? "" : " AND e.site_id = ?";
        List<Object> base = new ArrayList<>(List.of(tenant));
        if (siteId != null) base.add(siteId);

        List<OnSiteRow> onSite = jdbc.query("""
                SELECT e.id, e.site_id, s.name AS site_name, ap.name AS access_point_name, e.entry_type, e.person_name, e.company,
                       e.host_name, e.vehicle_registration, e.logged_in_at, e.status
                FROM security_gate_register_entries e
                LEFT JOIN security_sites s ON s.id = e.site_id
                LEFT JOIN security_access_points ap ON ap.id = e.access_point_id
                WHERE e.tenant_id = ? AND e.logged_out_at IS NULL"""
                + siteFilter + " ORDER BY e.logged_in_at LIMIT " + (ON_SITE_LIMIT + 1),
                (rs, n) -> row(rs), base.toArray());
        boolean truncated = onSite.size() > ON_SITE_LIMIT;
        if (truncated) onSite = new ArrayList<>(onSite.subList(0, ON_SITE_LIMIT));

        // Counts come from the database, not from the (capped) list above.
        Map<String, Integer> byType = new LinkedHashMap<>();
        int onSiteNow = 0, overstayed = 0;
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT e.entry_type, e.status, COUNT(*) AS n FROM security_gate_register_entries e
                WHERE e.tenant_id = ? AND e.logged_out_at IS NULL""" + siteFilter + " GROUP BY e.entry_type, e.status", base.toArray())) {
            int n = ((Number) r.get("n")).intValue();
            onSiteNow += n;
            if ("OVERSTAYED".equals(r.get("status"))) overstayed += n;
            byType.merge(String.valueOf(r.get("entry_type")), n, Integer::sum);
        }

        List<Object> withToday = new ArrayList<>(base); withToday.add(Timestamp.from(today));
        Integer entered = jdbc.queryForObject("SELECT COUNT(*) FROM security_gate_register_entries e WHERE e.tenant_id = ?" + siteFilter + " AND e.logged_in_at >= ?", Integer.class, withToday.toArray());
        Integer departed = jdbc.queryForObject("SELECT COUNT(*) FROM security_gate_register_entries e WHERE e.tenant_id = ?" + siteFilter + " AND e.logged_out_at >= ?", Integer.class, withToday.toArray());

        List<SiteCount> bySite = jdbc.query("""
                SELECT s.id, s.name,
                       COUNT(*) FILTER (WHERE e.logged_out_at IS NULL) AS on_site,
                       COUNT(*) FILTER (WHERE e.logged_in_at >= ?) AS entered_today
                FROM security_gate_register_entries e JOIN security_sites s ON s.id = e.site_id
                WHERE e.tenant_id = ?"""
                + siteFilter + " AND (e.logged_out_at IS NULL OR e.logged_in_at >= ?) GROUP BY s.id, s.name ORDER BY s.name",
                (rs, n) -> new SiteCount((UUID) rs.getObject("id"), rs.getString("name"), rs.getInt("on_site"), rs.getInt("entered_today")),
                bySiteParams(today, tenant, siteId));

        return new Dashboard(new Counts(onSiteNow, overstayed, nz(entered), nz(departed), byType), onSite, truncated, bySite, today);
    }

    private static Object[] bySiteParams(Instant today, UUID tenant, UUID siteId) {
        List<Object> p = new ArrayList<>(List.of(Timestamp.from(today), tenant));
        if (siteId != null) p.add(siteId);
        p.add(Timestamp.from(today));
        return p.toArray();
    }

    static OnSiteRow row(ResultSet rs) throws SQLException {
        return new OnSiteRow((UUID) rs.getObject("id"), (UUID) rs.getObject("site_id"), rs.getString("site_name"), rs.getString("access_point_name"),
                rs.getString("entry_type"), rs.getString("person_name"), rs.getString("company"), rs.getString("host_name"),
                rs.getString("vehicle_registration"), rs.getTimestamp("logged_in_at").toInstant(), rs.getString("status"));
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
}
