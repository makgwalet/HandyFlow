package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.DashboardSummary;
import za.co.handyflow.platform.clinic.dto.DashboardSummary.Item;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Counts and lists for the clinic dashboard. The counts are done by the database over the whole day,
 * so they stay right however many appointments the clinic has, and "today" is the clinic's own day
 * (Africa/Johannesburg), not the browser's or UTC's.
 */
@Service
@RequiredArgsConstructor
public class ClinicDashboardService {

    private static final int TODAY_LIST_LIMIT = 200;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public DashboardSummary summary(TenantId t) {
        return summary(t, Instant.now());
    }

    /** Same as {@link #summary(TenantId)} for a given moment (so tests do not depend on the clock). */
    @Transactional(readOnly = true)
    public DashboardSummary summary(TenantId t, Instant now) {
        Timestamp from = Timestamp.from(DashboardRules.dayStart(now, DashboardRules.CLINIC_ZONE));
        Timestamp to = Timestamp.from(DashboardRules.dayEnd(now, DashboardRules.CLINIC_ZONE));

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS n FROM clinic_appointments "
                        + "WHERE tenant_id = ? AND deleted_at IS NULL AND scheduled_at >= ? AND scheduled_at < ? "
                        + "GROUP BY status ORDER BY status",
                rs -> { byStatus.put(rs.getString("status"), rs.getInt("n")); },
                t.getValue(), from, to);

        Long patients = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clinic_patients WHERE tenant_id = ? AND deleted_at IS NULL", Long.class, t.getValue());

        List<Item> today = jdbc.query(ITEM_SQL + "WHERE a.tenant_id = ? AND a.deleted_at IS NULL "
                        + "AND a.scheduled_at >= ? AND a.scheduled_at < ? ORDER BY a.scheduled_at LIMIT " + TODAY_LIST_LIMIT,
                (rs, i) -> item(rs), t.getValue(), from, to);

        List<Item> next = jdbc.query(ITEM_SQL + "WHERE a.tenant_id = ? AND a.deleted_at IS NULL "
                        + "AND a.status IN ('SCHEDULED','CONFIRMED') AND a.scheduled_at >= ? "
                        + "ORDER BY a.scheduled_at LIMIT 1",
                (rs, i) -> item(rs), t.getValue(), Timestamp.from(now));

        return new DashboardSummary(
                DashboardRules.localDate(now, DashboardRules.CLINIC_ZONE).toString(),
                DashboardRules.CLINIC_ZONE.getId(),
                DashboardRules.total(byStatus),
                DashboardRules.awaiting(byStatus),
                DashboardRules.count(byStatus, "IN_PROGRESS"),
                DashboardRules.count(byStatus, "COMPLETED"),
                DashboardRules.count(byStatus, "CANCELLED"),
                DashboardRules.count(byStatus, "NO_SHOW"),
                patients == null ? 0 : patients,
                byStatus, today, next.isEmpty() ? null : next.get(0));
    }

    private static final String ITEM_SQL =
            "SELECT a.id, a.patient_id, a.practitioner_id, p.full_name AS patient_name, pr.full_name AS practitioner_name, a.scheduled_at, "
                    + "a.duration_minutes, a.appointment_type, a.status "
                    + "FROM clinic_appointments a "
                    + "JOIN clinic_patients p ON p.id = a.patient_id "
                    + "LEFT JOIN clinic_practitioners pr ON pr.id = a.practitioner_id ";

    private static Item item(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Item(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
                rs.getObject("practitioner_id", UUID.class), rs.getString("practitioner_name"),
                rs.getTimestamp("scheduled_at").toInstant(), rs.getInt("duration_minutes"),
                rs.getString("appointment_type"), rs.getString("status"));
    }
}
