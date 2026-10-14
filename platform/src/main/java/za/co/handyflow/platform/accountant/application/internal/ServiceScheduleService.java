package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.dto.PracticeServiceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recurring services per client (finance F22). The daily job only makes "due" runs; a person bills or skips each one, so
 * nothing posts revenue on its own.
 */
@Service
@RequiredArgsConstructor
public class ServiceScheduleService {

    private final JdbcTemplate jdbc;
    private final ClientAccessService access;
    private final AccountantService accountant;

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "SCHEDULE_REFUSED"); }
    private static HandyFlowException missing() { return new HandyFlowException("Not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    private static final String SELECT = """
            SELECT s.id, s.client_id, c.trading_name, s.service_type, s.description, s.frequency, s.first_date, s.end_date, s.fee_amount,
                   s.include_vat, s.due_days, s.budget_hours, s.owner_user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')),
                   s.status, (SELECT MAX(r.run_index) FROM acc_service_runs r WHERE r.schedule_id = s.id)
              FROM acc_service_schedules s
              JOIN acc_clients c ON c.id = s.client_id
              LEFT JOIN users u ON u.id = s.owner_user_id""";

    private static ScheduleResponse map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String freq = rs.getString(6);
        LocalDate first = rs.getDate(7).toLocalDate();
        LocalDate end = rs.getDate(8) == null ? null : rs.getDate(8).toLocalDate();
        String status = rs.getString(15);
        int last = rs.getObject(16) == null ? -1 : rs.getInt(16);
        LocalDate next = ServiceScheduleRules.runDate(first, freq, last + 1);
        if (!"ACTIVE".equals(status) || (end != null && next.isAfter(end))) next = null;
        String owner = rs.getString(14);
        return new ScheduleResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), freq,
                first, end, rs.getBigDecimal(9), rs.getBoolean(10), rs.getInt(11), rs.getBigDecimal(12), rs.getObject(13, UUID.class),
                owner == null || owner.isBlank() ? null : owner, status, next);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(TenantId tenantId, UUID clientId) {
        List<ScheduleResponse> all = clientId == null
                ? jdbc.query(SELECT + " WHERE s.tenant_id = ? ORDER BY c.trading_name, s.service_type", ServiceScheduleService::map, tenantId.getValue())
                : jdbc.query(SELECT + " WHERE s.tenant_id = ? AND s.client_id = ? ORDER BY s.service_type", ServiceScheduleService::map, tenantId.getValue(), clientId);
        if (clientId != null) access.require(tenantId, clientId);
        return clientId != null ? all : access.visible(tenantId, all, ScheduleResponse::clientId);
    }

    @Transactional
    public ScheduleResponse create(TenantId tenantId, UUID clientId, ScheduleRequest req, UUID by) {
        access.require(tenantId, clientId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM acc_clients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, clientId, tenantId.getValue()) == 0)
            throw new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        int due = req.dueDays() == null ? 30 : req.dueDays();
        String problem = ServiceScheduleRules.problem(req.serviceType(), req.frequency(), req.firstDate(), req.endDate(), req.feeAmount(), req.budgetHours(), due, req.description());
        if (problem != null) throw bad(problem);
        if (req.ownerUserId() != null && jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND tenant_id = ?", Integer.class, req.ownerUserId(), tenantId.getValue()) == 0)
            throw bad("The owner is not a user of this practice.");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO acc_service_schedules (id, tenant_id, client_id, service_type, description, frequency, first_date, end_date, fee_amount, include_vat, due_days, budget_hours, owner_user_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId.getValue(), clientId, req.serviceType(), req.description() == null || req.description().isBlank() ? null : req.description().trim(),
                req.frequency(), Date.valueOf(req.firstDate()), req.endDate() == null ? null : Date.valueOf(req.endDate()),
                req.feeAmount() == null || req.feeAmount().signum() == 0 ? null : req.feeAmount(), req.includeVat(), due, req.budgetHours(), req.ownerUserId(), by);
        return one(tenantId, id);
    }

    private ScheduleResponse one(TenantId tenantId, UUID id) {
        List<ScheduleResponse> r = jdbc.query(SELECT + " WHERE s.tenant_id = ? AND s.id = ?", ServiceScheduleService::map, tenantId.getValue(), id);
        if (r.isEmpty()) throw missing();
        access.require(tenantId, r.get(0).clientId());
        return r.get(0);
    }

    @Transactional
    public ScheduleResponse setStatus(TenantId tenantId, UUID id, String target) {
        ScheduleResponse cur = one(tenantId, id);
        String problem = ServiceScheduleRules.statusProblem(cur.status(), target);
        if (problem != null) throw bad(problem);
        jdbc.update("UPDATE acc_service_schedules SET status = ? WHERE id = ? AND tenant_id = ?", target, id, tenantId.getValue());
        return one(tenantId, id);
    }

    /** Makes the runs that have come due. Safe to repeat. {@code tenantId} null means every practice (the daily job). */
    @Transactional
    public int generateDue(UUID tenantId, LocalDate today) {
        record S(UUID id, UUID tenant, UUID client, String freq, LocalDate first, LocalDate end, int last) {}
        String sql = """
                SELECT s.id, s.tenant_id, s.client_id, s.frequency, s.first_date, s.end_date,
                       COALESCE((SELECT MAX(r.run_index) FROM acc_service_runs r WHERE r.schedule_id = s.id), -1)
                  FROM acc_service_schedules s
                 WHERE s.status = 'ACTIVE'""" + (tenantId == null ? "" : " AND s.tenant_id = ?");
        org.springframework.jdbc.core.RowMapper<S> mapper = (rs, n) -> new S(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(), rs.getInt(7));
        List<S> schedules = tenantId == null ? jdbc.query(sql, mapper) : jdbc.query(sql, mapper, tenantId);
        int made = 0;
        for (S s : schedules)
            for (int i : ServiceScheduleRules.dueIndexes(s.first(), s.freq(), s.end(), s.last(), today))
                made += jdbc.update("""
                        INSERT INTO acc_service_runs (id, tenant_id, schedule_id, client_id, run_index, run_date)
                        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (schedule_id, run_index) DO NOTHING""",
                        UUID.randomUUID(), s.tenant(), s.id(), s.client(), i, Date.valueOf(ServiceScheduleRules.runDate(s.first(), s.freq(), i)));
        return made;
    }

    private static final String RUN_SELECT = """
            SELECT r.id, r.schedule_id, r.client_id, c.trading_name, s.service_type, s.description, r.run_date, r.status, s.fee_amount, r.fee_note_id, r.note
              FROM acc_service_runs r
              JOIN acc_service_schedules s ON s.id = r.schedule_id
              JOIN acc_clients c ON c.id = r.client_id""";

    private static RunResponse runMap(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new RunResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getDate(7).toLocalDate(), rs.getString(8), rs.getBigDecimal(9), rs.getObject(10, UUID.class), rs.getString(11));
    }

    @Transactional(readOnly = true)
    public List<RunResponse> runs(TenantId tenantId, String status) {
        List<RunResponse> all = jdbc.query(RUN_SELECT + " WHERE r.tenant_id = ? AND r.status = ? ORDER BY r.run_date DESC LIMIT 300",
                ServiceScheduleService::runMap, tenantId.getValue(), status == null ? "DUE" : status);
        return access.visible(tenantId, all, RunResponse::clientId);
    }

    @Transactional
    public RunResponse bill(TenantId tenantId, UUID runId) {
        List<RunResponse> r = jdbc.query(RUN_SELECT + " WHERE r.tenant_id = ? AND r.id = ? FOR UPDATE OF r", ServiceScheduleService::runMap, tenantId.getValue(), runId);
        if (r.isEmpty()) throw missing();
        RunResponse run = r.get(0);
        access.require(tenantId, run.clientId());
        ScheduleResponse sch = one(tenantId, run.scheduleId());
        String problem = ServiceScheduleRules.billProblem(run.status(), sch.status(), sch.feeAmount());
        if (problem != null) throw bad(problem);
        LocalDate today = LocalDate.now();
        var note = accountant.generateFixedFeeNote(tenantId, run.clientId(), today, today.plusDays(sch.dueDays()), sch.feeAmount(),
                ServiceScheduleRules.runDescription(sch.serviceType(), sch.description(), run.runDate(), sch.frequency()), sch.includeVat());
        jdbc.update("UPDATE acc_service_runs SET status = 'BILLED', fee_note_id = ?, handled_by = ?, handled_at = now() WHERE id = ?",
                note.id(), currentUser(), runId);
        return jdbc.query(RUN_SELECT + " WHERE r.id = ?", ServiceScheduleService::runMap, runId).get(0);
    }

    @Transactional
    public RunResponse skip(TenantId tenantId, UUID runId, String noteText) {
        List<RunResponse> r = jdbc.query(RUN_SELECT + " WHERE r.tenant_id = ? AND r.id = ? FOR UPDATE OF r", ServiceScheduleService::runMap, tenantId.getValue(), runId);
        if (r.isEmpty()) throw missing();
        access.require(tenantId, r.get(0).clientId());
        String problem = ServiceScheduleRules.skipProblem(r.get(0).status(), noteText);
        if (problem != null) throw bad(problem);
        String n = noteText.trim();
        jdbc.update("UPDATE acc_service_runs SET status = 'SKIPPED', note = ?, handled_by = ?, handled_at = now() WHERE id = ?",
                n.substring(0, Math.min(n.length(), 500)), currentUser(), runId);
        return jdbc.query(RUN_SELECT + " WHERE r.id = ?", ServiceScheduleService::runMap, runId).get(0);
    }

    private static UUID currentUser() {
        try { return za.co.handyflow.platform.shared.TenantContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    static BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
