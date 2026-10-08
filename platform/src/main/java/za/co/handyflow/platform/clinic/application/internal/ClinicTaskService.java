package za.co.handyflow.platform.clinic.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.TaskDtos.CreateTaskRequest;
import za.co.handyflow.platform.clinic.dto.TaskDtos.TaskRow;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Clinic tasks (V366). */
@Service
public class ClinicTaskService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final String SELECT =
            "SELECT t.id, t.patient_id, p.full_name, t.assigned_to, t.kind, t.title, t.detail, t.due_date, t.status, t.source_type, "
          + "t.source_id, t.created_by, t.created_at, t.closed_at, t.closing_note FROM clinic_tasks t "
          + "LEFT JOIN clinic_patients p ON p.id = t.patient_id WHERE t.tenant_id = ? ";

    private final JdbcTemplate jdbc;

    public ClinicTaskService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static LocalDate today() { return LocalDate.now(SAST); }

    private TaskRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date due = rs.getDate(8);
        LocalDate dueDate = due == null ? null : due.toLocalDate();
        String status = rs.getString(9);
        Timestamp closed = rs.getTimestamp(14);
        return new TaskRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), (UUID) rs.getObject(4), rs.getString(5),
                rs.getString(6), rs.getString(7), dueDate, TaskRules.overdue(status, dueDate, today()), status, rs.getString(10),
                (UUID) rs.getObject(11), (UUID) rs.getObject(12), rs.getTimestamp(13).toInstant(),
                closed == null ? null : closed.toInstant(), rs.getString(15));
    }

    /** Open tasks, overdue first then by due date. {@code mine}: assigned to me or made by me. */
    @Transactional(readOnly = true)
    public List<TaskRow> open(UUID tenantId, UUID userId, boolean mine, int limit) {
        int cap = Math.max(1, Math.min(limit, 200));
        String sql = SELECT + "AND t.status = 'OPEN' " + (mine ? "AND (t.assigned_to = ? OR t.created_by = ?) " : "")
                + "ORDER BY (t.due_date IS NULL), t.due_date, t.created_at LIMIT " + cap;
        return mine
                ? jdbc.query(sql, (rs, i) -> row(rs), tenantId, userId, userId)
                : jdbc.query(sql, (rs, i) -> row(rs), tenantId);
    }

    @Transactional
    public TaskRow create(UUID tenantId, UUID userId, CreateTaskRequest r) {
        String kind = TaskRules.kind(r.kind());
        String title = TaskRules.title(r.title());
        String detail = TaskRules.detail(r.detail());
        LocalDate due = TaskRules.due(r.dueDate(), today());
        if (r.sourceId() != null) {
            // One open task per source and type: asking again returns the one that is already there.
            List<UUID> existing = jdbc.query("SELECT id FROM clinic_tasks WHERE tenant_id = ? AND source_type IS NOT DISTINCT FROM CAST(? AS varchar) AND source_id = ? AND kind = ? AND status = 'OPEN'",
                    (rs, i) -> (UUID) rs.getObject(1), tenantId, r.sourceType(), r.sourceId(), kind);
            if (!existing.isEmpty()) return get(tenantId, existing.get(0));
        }
        if (r.patientId() != null) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM clinic_patients WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, r.patientId());
            if (n == null || n == 0) throw new ResourceNotFoundException("Patient", r.patientId().toString());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_tasks (id, tenant_id, patient_id, assigned_to, kind, title, detail, due_date, source_type, source_id, created_by, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, tenantId, r.patientId(), r.assignedTo(), kind, title, detail, due == null ? null : java.sql.Date.valueOf(due),
                r.sourceType() == null || r.sourceType().isBlank() ? null : r.sourceType().trim(), r.sourceId(), userId, Timestamp.from(Instant.now()));
        return get(tenantId, id);
    }

    @Transactional(readOnly = true)
    public TaskRow get(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + "AND t.id = ?", (rs, i) -> row(rs), tenantId, id).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Task", id.toString()));
    }

    @Transactional
    public TaskRow complete(UUID tenantId, UUID userId, UUID id, String note) {
        return close(tenantId, userId, id, "DONE", TaskRules.doneNote(note));
    }

    @Transactional
    public TaskRow dismiss(UUID tenantId, UUID userId, UUID id, String reason) {
        return close(tenantId, userId, id, "DISMISSED", TaskRules.dismissReason(reason));
    }

    private TaskRow close(UUID tenantId, UUID userId, UUID id, String status, String note) {
        TaskRow current = get(tenantId, id);
        TaskRules.requireOpen(current.status());
        // The status guard in the WHERE clause stops two people closing the same task at once.
        int n = jdbc.update("UPDATE clinic_tasks SET status = ?, closed_by = ?, closed_at = ?, closing_note = ? WHERE tenant_id = ? AND id = ? AND status = 'OPEN'",
                status, userId, Timestamp.from(Instant.now()), note, tenantId, id);
        if (n == 0) throw new IllegalStateException("This task was just closed by someone else");
        return get(tenantId, id);
    }
}
