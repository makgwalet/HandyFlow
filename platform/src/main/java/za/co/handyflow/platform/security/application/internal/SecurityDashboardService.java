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

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int EXPIRING_DAYS = 30;
    static final int ROW_LIMIT = 6;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Dashboard dashboard(TenantId tenantId) { return dashboard(tenantId, Instant.now()); }

    @Transactional(readOnly = true)
    Dashboard dashboard(TenantId tenantId, Instant now) {
        UUID tenant = tenantId.getValue();
        LocalDate today = now.atZone(SAST).toLocalDate();
        Timestamp dayStart = utc(today.atStartOfDay(SAST).toInstant());
        Timestamp dayEnd = utc(today.plusDays(1).atStartOfDay(SAST).toInstant());
        Timestamp lateBefore = utc(now.minusSeconds(ShiftPunctuality.GRACE_MINUTES * 60L));
        Timestamp week = utc(now.minusSeconds(7L * 24 * 3600));
        Timestamp nowTs = utc(now);

        var shiftRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE status = 'ACTIVE') AS on_duty,
                       COUNT(*) FILTER (WHERE start_at >= ? AND start_at < ? AND status <> 'CANCELLED') AS scheduled_today,
                       COUNT(*) FILTER (WHERE status = 'SCHEDULED' AND start_at < ? AND end_at > ?) AS not_started,
                       COUNT(*) FILTER (WHERE status = 'MISSED' AND start_at >= ? AND start_at < ?) AS missed_today,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED' AND start_at >= ? AND start_at < ?) AS completed_today
                FROM security_shifts WHERE tenant_id = ? AND deleted_at IS NULL""",
                dayStart, dayEnd, lateBefore, nowTs, dayStart, dayEnd, dayStart, dayEnd, tenant);
        Shifts shifts = new Shifts(n(shiftRow, "on_duty"), n(shiftRow, "scheduled_today"), n(shiftRow, "not_started"),
                n(shiftRow, "missed_today"), n(shiftRow, "completed_today"));

        String psira = GuardDirectoryService.psiraStateSql(today);
        var guardRow = jdbc.queryForMap("SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE COALESCE(g.status, 'ACTIVE') = 'ACTIVE') AS active,"
                + " COUNT(*) FILTER (WHERE COALESCE(g.status, 'ACTIVE') = 'ACTIVE' AND " + psira + " = 'EXPIRED') AS psira_expired,"
                + " COUNT(*) FILTER (WHERE COALESCE(g.status, 'ACTIVE') = 'ACTIVE' AND " + psira + " = 'EXPIRING') AS psira_expiring"
                + " FROM security_guards g WHERE g.tenant_id = ? AND g.deleted_at IS NULL", tenant);

        var compRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE c.expiry_date < ?) AS expired,
                       COUNT(*) FILTER (WHERE c.expiry_date >= ? AND c.expiry_date <= ?) AS expiring
                FROM security_guard_competencies c JOIN security_guards g ON g.id = c.guard_id
                WHERE c.tenant_id = ? AND c.deleted_at IS NULL AND c.required AND c.expiry_date IS NOT NULL
                  AND g.deleted_at IS NULL AND COALESCE(g.status, 'ACTIVE') = 'ACTIVE'""",
                java.sql.Date.valueOf(today), java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.plusDays(EXPIRING_DAYS)), tenant);
        Workforce workforce = new Workforce(n(guardRow, "total"), n(guardRow, "active"), n(guardRow, "psira_expired"), n(guardRow, "psira_expiring"),
                n(compRow, "expired"), n(compRow, "expiring"));

        var incRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE status <> 'RESOLVED') AS open,
                       COUNT(*) FILTER (WHERE status = 'OPEN') AS unacknowledged,
                       COUNT(*) FILTER (WHERE status <> 'RESOLVED' AND UPPER(severity) = 'CRITICAL') AS critical_open,
                       COUNT(*) FILTER (WHERE created_at >= ?) AS last7
                FROM security_incidents WHERE tenant_id = ? AND deleted_at IS NULL""", week, tenant);
        Incidents incidents = new Incidents(n(incRow, "open"), n(incRow, "unacknowledged"), n(incRow, "critical_open"), n(incRow, "last7"));

        // Urgent is decided by the same rule the complaints screen uses, so the open complaints are read, not counted in SQL.
        int openComplaints = 0, urgent = 0;
        for (var r : jdbc.queryForList("SELECT category, severity FROM security_guard_complaints WHERE tenant_id = ? AND status NOT IN ('CLOSED', 'WITHDRAWN')", tenant)) {
            openComplaints++;
            if (urgentComplaint(String.valueOf(r.get("category")), String.valueOf(r.get("severity")))) urgent++;
        }
        Complaints complaints = new Complaints(openComplaints, urgent);

        var alarmRow = jdbc.queryForMap("SELECT COUNT(*) AS open, COUNT(*) FILTER (WHERE status = 'NEW') AS new_alarms FROM security_alarm_events"
                + " WHERE tenant_id = ? AND status IN ('NEW', 'TRIAGED', 'DISPATCHED')", tenant);

        var gateRow = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE logged_out_at IS NULL) AS on_site,
                       COUNT(*) FILTER (WHERE logged_out_at IS NULL AND status = 'OVERSTAYED') AS overstayed,
                       COUNT(*) FILTER (WHERE logged_in_at >= ?) AS entered_today
                FROM security_gate_register_entries WHERE tenant_id = ?""", dayStartTz(today), tenant);
        Gate gate = new Gate(n(gateRow, "on_site"), n(gateRow, "overstayed"), n(gateRow, "entered_today"));

        Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM security_sites WHERE tenant_id = ? AND deleted_at IS NULL AND active", Integer.class, tenant);

        List<ActiveShiftRow> active = jdbc.query("""
                SELECT s.id, s.guard_id, TRIM(g.first_name || ' ' || g.last_name) AS guard_name, s.site_id, st.name AS site_name,
                       s.start_at, s.end_at, s.actual_start_at
                FROM security_shifts s
                LEFT JOIN security_guards g ON g.id = s.guard_id
                LEFT JOIN security_sites st ON st.id = s.site_id
                WHERE s.tenant_id = ? AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                ORDER BY s.start_at, s.id LIMIT """ + ROW_LIMIT,
                (rs, i) -> {
                    Instant start = rs.getTimestamp("start_at").toLocalDateTime().toInstant(ZoneOffset.UTC);
                    Timestamp actual = rs.getTimestamp("actual_start_at");
                    Instant actualAt = actual == null ? null : actual.toLocalDateTime().toInstant(ZoneOffset.UTC);
                    return new ActiveShiftRow((UUID) rs.getObject("id"), (UUID) rs.getObject("guard_id"), rs.getString("guard_name"),
                            (UUID) rs.getObject("site_id"), rs.getString("site_name"), start,
                            rs.getTimestamp("end_at").toLocalDateTime().toInstant(ZoneOffset.UTC), actualAt,
                            ShiftPunctuality.minutesLate(start, actualAt));
                }, tenant);

        List<OpenIncidentRow> openRows = jdbc.query("""
                SELECT i.id, i.title, i.severity, i.status, i.site_id, st.name AS site_name, i.created_at
                FROM security_incidents i LEFT JOIN security_sites st ON st.id = i.site_id
                WHERE i.tenant_id = ? AND i.deleted_at IS NULL AND i.status <> 'RESOLVED'
                ORDER BY CASE UPPER(i.severity) WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, i.created_at DESC, i.id
                LIMIT """ + ROW_LIMIT,
                (rs, i) -> new OpenIncidentRow((UUID) rs.getObject("id"), rs.getString("title"), rs.getString("severity"), rs.getString("status"),
                        (UUID) rs.getObject("site_id"), rs.getString("site_name"),
                        rs.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC)), tenant);

        int openAlarms = n(alarmRow, "open");
        var attention = DashboardAttention.build(openAlarms, n(alarmRow, "new_alarms"), shifts, workforce, incidents, complaints, gate);
        return new Dashboard(now, sites == null ? 0 : sites, openAlarms, shifts, workforce, incidents, complaints, gate, attention, active, openRows);
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
    private static Timestamp dayStartTz(LocalDate today) { return Timestamp.from(today.atStartOfDay(SAST).toInstant()); }
}
