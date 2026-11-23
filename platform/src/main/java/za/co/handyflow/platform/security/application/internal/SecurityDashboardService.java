// security/application/internal/SecurityDashboardService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The figures behind the Security landing dashboard. Everything is counted in the database (the page used to fetch
 * up to 100 guards and 50 shifts and count them in the browser). "Today" is South African time. Shift and incident
 * times are stored as UTC without a zone, so the bounds below are passed as UTC timestamps.
 */
@Service
@RequiredArgsConstructor
public class SecurityDashboardService {

    
    static final int EXPIRING_DAYS = 30;
    static final int ROW_LIMIT = 6;

    /** Optional so a service built by hand still works; without it South Africa's zone is used, never the server's. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    private final JdbcTemplate jdbc;

    /** Optional, so the dashboard still works without the coverage feature. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CoverageService coverage;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WeeklyHoursService weeklyHours;

    @Transactional(readOnly = true)
    public Dashboard dashboard(TenantId tenantId) { return dashboard(tenantId, Instant.now(), null); }

    /**
     * `allowedSites` null = the whole company. Otherwise only what happens at those sites is counted and listed, and the figures that
     * are not about a site (guards and complaints) are left out (null), not shown as zero.
     */
    @Transactional(readOnly = true)
    public Dashboard dashboard(TenantId tenantId, java.util.Set<UUID> allowedSites) { return dashboard(tenantId, Instant.now(), allowedSites); }

    @Transactional(readOnly = true)
    Dashboard dashboard(TenantId tenantId, Instant now) { return dashboard(tenantId, now, null); }

    /** The values for a site filter, appended after the other parameters. */
    private static Object[] with(java.util.Set<UUID> allowed, Object... base) {
        java.util.List<Object> all = new java.util.ArrayList<>(java.util.Arrays.asList(base));
        all.addAll(SiteScopeRules.sqlArgs(allowed));
        return all.toArray();
    }

    @Transactional(readOnly = true)
    Dashboard dashboard(TenantId tenantId, Instant now, java.util.Set<UUID> allowed) {
        UUID tenant = tenantId.getValue();
        boolean limited = allowed != null;
        ZoneId zone = za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId);
        LocalDate today = now.atZone(zone).toLocalDate();
        Timestamp dayStart = utc(today.atStartOfDay(zone).toInstant());
        Timestamp dayEnd = utc(today.plusDays(1).atStartOfDay(zone).toInstant());
        Timestamp lateBefore = utc(now.minusSeconds(ShiftPunctuality.GRACE_MINUTES * 60L));
        Timestamp week = utc(now.minusSeconds(7L * 24 * 3600));
        Timestamp nowTs = utc(now);

        var shiftRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE status = 'ACTIVE') AS on_duty,
                       COUNT(*) FILTER (WHERE start_at >= ? AND start_at < ? AND status <> 'CANCELLED') AS scheduled_today,
                       COUNT(*) FILTER (WHERE status = 'SCHEDULED' AND start_at < ? AND end_at > ?) AS not_started,
                       COUNT(*) FILTER (WHERE status = 'MISSED' AND start_at >= ? AND start_at < ?) AS missed_today,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED' AND start_at >= ? AND start_at < ?) AS completed_today
                FROM security_shifts WHERE tenant_id = ? AND deleted_at IS NULL""" + SiteScopeRules.sqlFilter("site_id", allowed),
                with(allowed, dayStart, dayEnd, lateBefore, nowTs, dayStart, dayEnd, dayStart, dayEnd, tenant));
        Shifts shifts = new Shifts(n(shiftRow, "on_duty"), n(shiftRow, "scheduled_today"), n(shiftRow, "not_started"),
                n(shiftRow, "missed_today"), n(shiftRow, "completed_today"));

        String psira = GuardDirectoryService.psiraStateSql(today);
        var guardRow = limited ? java.util.Map.<String, Object>of() : jdbc.queryForMap("SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE COALESCE(g.status, 'ACTIVE') = 'ACTIVE') AS active,"
                + " COUNT(*) FILTER (WHERE COALESCE(g.status, 'ACTIVE') = 'ACTIVE' AND " + psira + " = 'EXPIRED') AS psira_expired,"
                + " COUNT(*) FILTER (WHERE COALESCE(g.status, 'ACTIVE') = 'ACTIVE' AND " + psira + " = 'EXPIRING') AS psira_expiring"
                + " FROM security_guards g WHERE g.tenant_id = ? AND g.deleted_at IS NULL", tenant);

        var compRow = limited ? java.util.Map.<String, Object>of() : jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE c.expiry_date < ?) AS expired,
                       COUNT(*) FILTER (WHERE c.expiry_date >= ? AND c.expiry_date <= ?) AS expiring
                FROM security_guard_competencies c JOIN security_guards g ON g.id = c.guard_id
                WHERE c.tenant_id = ? AND c.deleted_at IS NULL AND c.required AND c.expiry_date IS NOT NULL
                  AND g.deleted_at IS NULL AND COALESCE(g.status, 'ACTIVE') = 'ACTIVE'""",
                java.sql.Date.valueOf(today), java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.plusDays(EXPIRING_DAYS)), tenant);
        Workforce workforce = limited ? null : new Workforce(n(guardRow, "total"), n(guardRow, "active"), n(guardRow, "psira_expired"), n(guardRow, "psira_expiring"),
                n(compRow, "expired"), n(compRow, "expiring"));

        var incRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE status <> 'RESOLVED') AS open,
                       COUNT(*) FILTER (WHERE status = 'OPEN') AS unacknowledged,
                       COUNT(*) FILTER (WHERE status <> 'RESOLVED' AND UPPER(severity) = 'CRITICAL') AS critical_open,
                       COUNT(*) FILTER (WHERE created_at >= ?) AS last7
                FROM security_incidents WHERE tenant_id = ? AND deleted_at IS NULL""" + SiteScopeRules.sqlFilter("site_id", allowed),
                with(allowed, week, tenant));
        Incidents incidents = new Incidents(n(incRow, "open"), n(incRow, "unacknowledged"), n(incRow, "critical_open"), n(incRow, "last7"));

        // Urgent is decided by the same rule the complaints screen uses, so the open complaints are read, not counted in SQL.
        int openComplaints = 0, urgent = 0;
        if (!limited) {
            for (var r : jdbc.queryForList("SELECT category, severity FROM security_guard_complaints WHERE tenant_id = ? AND status NOT IN ('CLOSED', 'WITHDRAWN')", tenant)) {
                openComplaints++;
                if (urgentComplaint(String.valueOf(r.get("category")), String.valueOf(r.get("severity")))) urgent++;
            }
        }
        Complaints complaints = limited ? null : new Complaints(openComplaints, urgent);

        var alarmRow = jdbc.queryForMap("SELECT COUNT(*) AS open, COUNT(*) FILTER (WHERE status = 'NEW') AS new_alarms FROM security_alarm_events"
                + " WHERE tenant_id = ? AND status IN ('NEW', 'TRIAGED', 'DISPATCHED')" + SiteScopeRules.sqlFilter("site_id", allowed), with(allowed, tenant));

        var gateRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE logged_out_at IS NULL) AS on_site,
                       COUNT(*) FILTER (WHERE logged_out_at IS NULL AND status = 'OVERSTAYED') AS overstayed,
                       COUNT(*) FILTER (WHERE logged_in_at >= ?) AS entered_today
                FROM security_gate_register_entries WHERE tenant_id = ?""" + SiteScopeRules.sqlFilter("site_id", allowed), with(allowed, dayStartTz(today, zone), tenant));
        Gate gate = new Gate(n(gateRow, "on_site"), n(gateRow, "overstayed"), n(gateRow, "entered_today"));

        Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM security_sites WHERE tenant_id = ? AND deleted_at IS NULL AND active" + SiteScopeRules.sqlFilter("id", allowed), Integer.class, with(allowed, tenant));

        List<ActiveShiftRow> active = jdbc.query("""
                SELECT s.id, s.guard_id, TRIM(g.first_name || ' ' || g.last_name) AS guard_name, s.site_id, st.name AS site_name,
                       s.start_at, s.end_at, s.actual_start_at
                FROM security_shifts s
                LEFT JOIN security_guards g ON g.id = s.guard_id
                LEFT JOIN security_sites st ON st.id = s.site_id
                WHERE s.tenant_id = ? AND s.deleted_at IS NULL AND s.status = 'ACTIVE'""" + SiteScopeRules.sqlFilter("s.site_id", allowed)
                + " ORDER BY s.start_at, s.id LIMIT ?",
                (rs, i) -> {
                    Instant start = rs.getTimestamp("start_at").toLocalDateTime().toInstant(ZoneOffset.UTC);
                    Timestamp actual = rs.getTimestamp("actual_start_at");
                    Instant actualAt = actual == null ? null : actual.toLocalDateTime().toInstant(ZoneOffset.UTC);
                    return new ActiveShiftRow((UUID) rs.getObject("id"), (UUID) rs.getObject("guard_id"), rs.getString("guard_name"),
                            (UUID) rs.getObject("site_id"), rs.getString("site_name"), start,
                            rs.getTimestamp("end_at").toLocalDateTime().toInstant(ZoneOffset.UTC), actualAt,
                            ShiftPunctuality.minutesLate(start, actualAt));
                }, withLimit(allowed, tenant));

        List<OpenIncidentRow> openRows = jdbc.query("""
                SELECT i.id, i.title, i.severity, i.status, i.site_id, st.name AS site_name, i.created_at
                FROM security_incidents i LEFT JOIN security_sites st ON st.id = i.site_id
                WHERE i.tenant_id = ? AND i.deleted_at IS NULL AND i.status <> 'RESOLVED'""" + SiteScopeRules.sqlFilter("i.site_id", allowed)
                + " ORDER BY CASE UPPER(i.severity) WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, i.created_at DESC, i.id LIMIT ?",
                (rs, i) -> new OpenIncidentRow((UUID) rs.getObject("id"), rs.getString("title"), rs.getString("severity"), rs.getString("status"),
                        (UUID) rs.getObject("site_id"), rs.getString("site_name"),
                        rs.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC)), withLimit(allowed, tenant));

        int openAlarms = n(alarmRow, "open");
        // Coverage and weekly hours are counted for the whole company, so a user limited to some sites does not get them (as with guards).
        int[] cov = limited ? new int[]{0, 0} : coverageCounts(tenantId, now);
        HoursAttention.Counts hours = limited ? HoursAttention.Counts.NONE : hoursCounts(tenantId, now);
        var attention = DashboardAttention.build(openAlarms, n(alarmRow, "new_alarms"), shifts, workforce, incidents, complaints, gate, cov[0], cov[1], hours);
        return new Dashboard(now, sites == null ? 0 : sites, openAlarms, shifts, workforce, incidents, complaints, gate, attention, active, openRows);
    }

    /** tenant, then the site values, then the row limit: the order of the placeholders in the list queries. */
    private static Object[] withLimit(java.util.Set<UUID> allowed, UUID tenant) {
        java.util.List<Object> all = new java.util.ArrayList<>();
        all.add(tenant);
        all.addAll(SiteScopeRules.sqlArgs(allowed));
        all.add(ROW_LIMIT);
        return all.toArray();
    }

    static boolean urgentComplaint(String category, String severity) {
        try {
            return ComplaintWorkflow.isUrgent(ComplaintWorkflow.Category.valueOf(category), ComplaintWorkflow.Severity.valueOf(severity));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int n(java.util.Map<String, Object> row, String key) { Object v = row.get(key); return v == null ? 0 : ((Number) v).intValue(); }

    /** The database stores these columns as UTC without a zone, so the bound is the UTC wall clock of the instant. */
    private static Timestamp utc(Instant i) { return Timestamp.valueOf(java.time.LocalDateTime.ofInstant(i, ZoneOffset.UTC)); }

    /** Gate times are stored with a zone, so the bound is the instant itself. */
    private static Timestamp dayStartTz(LocalDate today, ZoneId zone) { return Timestamp.from(today.atStartOfDay(zone).toInstant()); }

    /** Coverage never breaks the dashboard: if it cannot be worked out, it simply adds nothing. */
    private int[] coverageCounts(TenantId tenantId, Instant now) {
        if (coverage == null) return new int[]{0, 0};
        try { return coverage.attentionCounts(tenantId, now); }
        catch (RuntimeException e) { return new int[]{0, 0}; }
    }

    /** Weekly hours never break the dashboard either. */
    private HoursAttention.Counts hoursCounts(TenantId tenantId, Instant now) {
        if (weeklyHours == null) return HoursAttention.Counts.NONE;
        try { return weeklyHours.attentionCounts(tenantId, now); }
        catch (RuntimeException e) { return HoursAttention.Counts.NONE; }
    }
}
