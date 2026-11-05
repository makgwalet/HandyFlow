package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.EmployeeRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ending employment is asked for by one HR manager and decided by another. The employee is not touched until the
 * decision. Where nobody else holds {@value #PERMISSION}, the request is carried out at once and recorded as a
 * sole-approver exception for the Owner's report.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminationApprovalService {

    static final String PERMISSION = "HR_MANAGE";

    private final JdbcTemplate jdbc;
    private final HrService hrService;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;
    private final SeparationOfDutiesChecker duties;

    /** @param pending true when the request now waits for another HR manager; {@code done} is then null */
    public record Outcome(boolean pending, UUID requestId, HrService.Terminated done) {}

    public record Pending(UUID id, UUID employeeId, String employeeName, LocalDate endDate, String reason, String endType,
                          LocalDate noticeGivenOn, UUID requestedBy, String requestedByName, Instant requestedAt) {}

    @Transactional
    public Outcome request(TenantId tenantId, UUID employeeId, LocalDate endDate, String reason, String endType,
                           LocalDate noticeGivenOn, UUID actor) {
        HrEmployee emp = employeeRepo.findActiveById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));
        String problem = EmployeeRules.terminationProblem(emp.getStatus(), emp.getStartDate(), endDate, reason, LocalDate.now());
        if (problem != null) throw new IllegalArgumentException(problem);

        if (actor == null || duties.otherHolders(tenantId, PERMISSION, actor) == 0) {
            if (actor != null) duties.recordSoleException(tenantId, actor, PERMISSION, "TERMINATION", employeeId,
                    "ended employment of " + emp.getEmployeeNumber() + " without a second approver (nobody else can approve)");
            return new Outcome(false, null, hrService.terminateEmployee(tenantId, employeeId, endDate, reason, endType, noticeGivenOn, actor));
        }
        Integer waiting = jdbc.queryForObject("SELECT count(*) FROM hr_termination_requests WHERE tenant_id = ? AND employee_id = ? AND status = 'PENDING'",
                Integer.class, tenantId.getValue(), employeeId);
        if (waiting != null && waiting > 0)
            throw new IllegalStateException("A termination for this employee is already waiting for a decision. Cancel it or have it decided first.");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hr_termination_requests (id, tenant_id, employee_id, end_date, reason, end_type, notice_given_on, requested_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId.getValue(), employeeId, Date.valueOf(endDate), reason.trim(), blankToNull(endType),
                noticeGivenOn == null ? null : Date.valueOf(noticeGivenOn), actor);
        audit.record(tenantId, employeeId, "TERMINATION_REQUESTED", "last day " + endDate, actor);
        return new Outcome(true, id, null);
    }

    @Transactional(readOnly = true)
    public List<Pending> pending(TenantId tenantId) {
        return jdbc.query("""
                SELECT r.id, r.employee_id, e.first_name || ' ' || e.last_name AS employee_name, r.end_date, r.reason, r.end_type,
                       r.notice_given_on, r.requested_by, trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS requested_by_name,
                       r.requested_at
                  FROM hr_termination_requests r
                  JOIN hr_employees e ON e.id = r.employee_id
                  LEFT JOIN users u ON u.id = r.requested_by
                 WHERE r.tenant_id = ? AND r.status = 'PENDING'
                 ORDER BY r.requested_at""",
                (rs, i) -> new Pending(rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class), rs.getString("employee_name"),
                        rs.getDate("end_date").toLocalDate(), rs.getString("reason"), rs.getString("end_type"),
                        rs.getDate("notice_given_on") == null ? null : rs.getDate("notice_given_on").toLocalDate(),
                        rs.getObject("requested_by", UUID.class), rs.getString("requested_by_name"),
                        rs.getTimestamp("requested_at").toInstant()),
                tenantId.getValue());
    }

    @Transactional
    public HrService.Terminated approve(TenantId tenantId, UUID requestId, String note, UUID actor) {
        Row r = lockPending(tenantId, requestId);
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        duties.require(tenantId, PERMISSION, r.requestedBy, actor,
                "The person who asked for a termination cannot approve it. Ask another HR manager.",
                "TERMINATION", r.employeeId, "approved their own termination request");
        HrService.Terminated done = hrService.terminateEmployee(tenantId, r.employeeId, r.endDate, r.reason, r.endType, r.noticeGivenOn, actor);
        decide(requestId, "APPROVED", actor, note);
        audit.record(tenantId, r.employeeId, "TERMINATION_APPROVED", "last day " + r.endDate, actor);
        return done;
    }

    @Transactional
    public void reject(TenantId tenantId, UUID requestId, String note, UUID actor) {
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Give a reason for declining the termination.");
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        Row r = lockPending(tenantId, requestId);
        decide(requestId, "REJECTED", actor, note.trim());
        audit.record(tenantId, r.employeeId, "TERMINATION_REJECTED", "last day " + r.endDate, actor);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID requestId, UUID actor) {
        Row r = lockPending(tenantId, requestId);
        if (actor == null || !actor.equals(r.requestedBy))
            throw new AccessDeniedException("Only the person who asked for the termination can cancel it.");
        decide(requestId, "CANCELLED", actor, null);
        audit.record(tenantId, r.employeeId, "TERMINATION_CANCELLED", "last day " + r.endDate, actor);
    }

    // ── internals ───────────────────────────────────────────────────────────────

    private record Row(UUID employeeId, LocalDate endDate, String reason, String endType, LocalDate noticeGivenOn, UUID requestedBy, String status) {}

    private Row lockPending(TenantId tenantId, UUID requestId) {
        List<Row> rows = jdbc.query("""
                SELECT employee_id, end_date, reason, end_type, notice_given_on, requested_by, status
                  FROM hr_termination_requests WHERE tenant_id = ? AND id = ? FOR UPDATE""",
                (rs, i) -> new Row(rs.getObject("employee_id", UUID.class), rs.getDate("end_date").toLocalDate(), rs.getString("reason"),
                        rs.getString("end_type"), rs.getDate("notice_given_on") == null ? null : rs.getDate("notice_given_on").toLocalDate(),
                        rs.getObject("requested_by", UUID.class), rs.getString("status")),
                tenantId.getValue(), requestId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Termination request", requestId.toString());
        Row r = rows.get(0);
        if (!"PENDING".equals(r.status))
            throw new HandyFlowException("This termination has already been decided.", org.springframework.http.HttpStatus.CONFLICT, "TERMINATION_DECIDED");
        return r;
    }

    private void decide(UUID requestId, String status, UUID actor, String note) {
        jdbc.update("UPDATE hr_termination_requests SET status = ?, decided_by = ?, decided_at = now(), decision_note = ? WHERE id = ?",
                status, actor, note, requestId);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
