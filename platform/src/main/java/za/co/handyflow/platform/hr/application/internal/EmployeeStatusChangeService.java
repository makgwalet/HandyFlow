package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.EmployeeStatusRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Suspending and reinstating an employee. A change is asked for by one HR manager and decided by another; nobody
 * approves their own. Where nobody else holds HR_MANAGE the change is applied and recorded as an audited sole-approver
 * exception. An emergency security or safety suspension is applied at once, and another HR manager must review it by the
 * end of the next business day (confirm it, or reverse it). Every step is written to the employee's audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeStatusChangeService {

    static final String PERMISSION = "HR_MANAGE";

    private final JdbcTemplate jdbc;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;
    private final SeparationOfDutiesChecker duties;

    /** @param applied true when the change is already in effect; false when it waits for another HR manager */
    public record Outcome(boolean applied, UUID requestId, boolean emergency, Instant reviewDueAt) {}

    public record Item(UUID id, UUID employeeId, String employeeName, String fromStatus, String toStatus, String reason, boolean emergency,
                       String status, UUID requestedBy, String requestedByName, Instant requestedAt, Instant reviewDueAt, boolean reviewOverdue) {}

    public record Waiting(List<Item> approvals, List<Item> reviews) {}

    @Transactional
    public Outcome request(TenantId tenantId, UUID employeeId, String toStatus, String reason, boolean emergency, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employeeRepo.findActiveById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));
        String problem = EmployeeStatusRules.problem(emp.getStatus(), toStatus, reason, emergency);
        if (problem != null) throw new IllegalArgumentException(problem);
        Integer waiting = jdbc.queryForObject("SELECT count(*) FROM hr_status_change_requests WHERE tenant_id = ? AND employee_id = ? AND status = 'PENDING'",
                Integer.class, tenantId.getValue(), employeeId);
        if (waiting != null && waiting > 0)
            throw new IllegalStateException("A status change for this employee is already waiting for a decision. Cancel it or have it decided first.");

        UUID id = UUID.randomUUID();
        String from = emp.getStatus();
        String why = reason.trim();
        if (emergency) {
            Instant due = EmployeeStatusRules.reviewDueAt(Instant.now());
            apply(emp, toStatus);
            jdbc.update("""
                    INSERT INTO hr_status_change_requests (id, tenant_id, employee_id, from_status, to_status, reason, emergency, status, requested_by,
                                                           applied_at, review_due_at)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE, 'APPLIED', ?, now(), ?)""",
                    id, tenantId.getValue(), employeeId, from, toStatus, why, actor, Timestamp.from(due));
            audit.record(tenantId, employeeId, "SUSPENDED_EMERGENCY", "reason: " + why + "; independent review due " + due, actor);
            log.warn("HR-EMERGENCY-SUSPENSION tenant={} employee={} by={} reviewDue={}", tenantId.getValue(), employeeId, actor, due);
            return new Outcome(true, id, true, due);
        }
        boolean sole = duties.otherHolders(tenantId, PERMISSION, actor) == 0;
        if (sole) {
            apply(emp, toStatus);
            jdbc.update("""
                    INSERT INTO hr_status_change_requests (id, tenant_id, employee_id, from_status, to_status, reason, status, requested_by, applied_at, sole_exception)
                    VALUES (?, ?, ?, ?, ?, ?, 'APPLIED', ?, now(), TRUE)""", id, tenantId.getValue(), employeeId, from, toStatus, why, actor);
            duties.recordSoleException(tenantId, actor, PERMISSION, "EMPLOYEE_STATUS", employeeId,
                    (EmployeeStatusRules.SUSPENDED.equals(toStatus) ? "suspended " : "reinstated ") + emp.getEmployeeNumber() + " without a second approver (nobody else can approve)",
                    Map.of("reason", why, "initiator", actor, "approver", actor, "from", from, "to", toStatus, "requestedAt", Instant.now().toString()));
            audit.record(tenantId, employeeId, "STATUS_" + toStatus, "reason: " + why + "; sole-approver exception", actor);
            return new Outcome(true, id, false, null);
        }
        jdbc.update("""
                INSERT INTO hr_status_change_requests (id, tenant_id, employee_id, from_status, to_status, reason, requested_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", id, tenantId.getValue(), employeeId, from, toStatus, why, actor);
        audit.record(tenantId, employeeId, "STATUS_" + toStatus + "_REQUESTED", "reason: " + why, actor);
        return new Outcome(false, id, false, null);
    }

    @Transactional
    public void approve(TenantId tenantId, UUID requestId, String note, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        Row r = lock(tenantId, requestId);
        if (!"PENDING".equals(r.status)) throw new HandyFlowException("This request has already been decided.", HttpStatus.CONFLICT, "STATUS_CHANGE_DECIDED");
        duties.require(tenantId, PERMISSION, r.requestedBy, actor, "The person who asked for this status change cannot approve it. Ask another HR manager.",
                "EMPLOYEE_STATUS", r.employeeId, "approved their own status change");
        HrEmployee emp = employeeRepo.findActiveById(tenantId, r.employeeId).orElseThrow(() -> new ResourceNotFoundException("Employee", r.employeeId.toString()));
        String problem = EmployeeStatusRules.problem(emp.getStatus(), r.toStatus, r.reason, false);
        if (problem != null) throw new HandyFlowException("This can no longer be applied: " + problem, HttpStatus.CONFLICT, "STATUS_CHANGE_STALE");
        apply(emp, r.toStatus);
        jdbc.update("UPDATE hr_status_change_requests SET status = 'APPLIED', decided_by = ?, decided_at = now(), decision_note = ?, applied_at = now() WHERE id = ?",
                actor, blank(note), requestId);
        audit.record(tenantId, r.employeeId, "STATUS_" + r.toStatus + "_APPROVED", "reason: " + r.reason, actor);
    }

    @Transactional
    public void reject(TenantId tenantId, UUID requestId, String note, UUID actor) {
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Give a reason for declining the change.");
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        Row r = lock(tenantId, requestId);
        if (!"PENDING".equals(r.status)) throw new HandyFlowException("This request has already been decided.", HttpStatus.CONFLICT, "STATUS_CHANGE_DECIDED");
        jdbc.update("UPDATE hr_status_change_requests SET status = 'REJECTED', decided_by = ?, decided_at = now(), decision_note = ? WHERE id = ?", actor, note.trim(), requestId);
        audit.record(tenantId, r.employeeId, "STATUS_" + r.toStatus + "_REJECTED", note.trim(), actor);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID requestId, UUID actor) {
        Row r = lock(tenantId, requestId);
        if (!"PENDING".equals(r.status)) throw new HandyFlowException("This request has already been decided.", HttpStatus.CONFLICT, "STATUS_CHANGE_DECIDED");
        if (actor == null || !actor.equals(r.requestedBy)) throw new AccessDeniedException("Only the person who asked for the change can cancel it.");
        jdbc.update("UPDATE hr_status_change_requests SET status = 'CANCELLED', decided_by = ?, decided_at = now() WHERE id = ?", actor, requestId);
        audit.record(tenantId, r.employeeId, "STATUS_" + r.toStatus + "_CANCELLED", null, actor);
    }

    /** Independent review of an emergency suspension: CONFIRM it, or REVERSE it (the employee is reinstated). */
    @Transactional
    public void review(TenantId tenantId, UUID requestId, boolean confirm, String note, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (note == null || note.trim().length() < EmployeeStatusRules.MIN_REASON) throw new IllegalArgumentException("Write the outcome of the review (at least " + EmployeeStatusRules.MIN_REASON + " characters).");
        Row r = lock(tenantId, requestId);
        if (!r.emergency) throw new IllegalArgumentException("Only an emergency suspension needs this review.");
        if (r.reviewOutcome != null) throw new HandyFlowException("This suspension has already been reviewed.", HttpStatus.CONFLICT, "ALREADY_REVIEWED");
        duties.require(tenantId, PERMISSION, r.requestedBy, actor, "The person who made the emergency suspension cannot review it. Ask another HR manager.",
                "EMPLOYEE_STATUS_REVIEW", r.employeeId, "reviewed their own emergency suspension");
        if (!confirm) {
            HrEmployee emp = employeeRepo.findActiveById(tenantId, r.employeeId).orElseThrow(() -> new ResourceNotFoundException("Employee", r.employeeId.toString()));
            if (EmployeeStatusRules.SUSPENDED.equals(emp.getStatus())) apply(emp, EmployeeStatusRules.ACTIVE);
        }
        jdbc.update("UPDATE hr_status_change_requests SET review_outcome = ?, reviewed_by = ?, reviewed_at = now(), review_note = ? WHERE id = ?",
                confirm ? "CONFIRMED" : "REVERSED", actor, note.trim(), requestId);
        audit.record(tenantId, r.employeeId, confirm ? "EMERGENCY_SUSPENSION_CONFIRMED" : "EMERGENCY_SUSPENSION_REVERSED", note.trim(), actor);
    }

    /** What is waiting on an HR manager: requests to approve, and emergency suspensions to review (overdue ones flagged). */
    @Transactional(readOnly = true)
    public Waiting waiting(TenantId tenantId) {
        List<Item> all = jdbc.query("""
                SELECT r.id, r.employee_id, e.first_name || ' ' || e.last_name AS employee_name, r.from_status, r.to_status, r.reason, r.emergency, r.status,
                       r.requested_by, trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS requested_by_name, r.requested_at, r.review_due_at,
                       r.review_outcome
                  FROM hr_status_change_requests r
                  JOIN hr_employees e ON e.id = r.employee_id
                  LEFT JOIN users u ON u.id = r.requested_by
                 WHERE r.tenant_id = ? AND (r.status = 'PENDING' OR (r.emergency AND r.review_outcome IS NULL))
                 ORDER BY r.requested_at""",
                (rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class), rs.getString("employee_name"),
                        rs.getString("from_status"), rs.getString("to_status"), rs.getString("reason"), rs.getBoolean("emergency"), rs.getString("status"),
                        rs.getObject("requested_by", UUID.class), rs.getString("requested_by_name"), rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("review_due_at") == null ? null : rs.getTimestamp("review_due_at").toInstant(),
                        rs.getTimestamp("review_due_at") != null && rs.getString("review_outcome") == null
                                && EmployeeStatusRules.reviewOverdue(rs.getTimestamp("review_due_at").toInstant(), Instant.now())),
                tenantId.getValue());
        return new Waiting(all.stream().filter(x -> "PENDING".equals(x.status())).toList(),
                all.stream().filter(x -> x.emergency() && "APPLIED".equals(x.status())).toList());
    }

    // ── internals ───────────────────────────────────────────────────────────────

    record Row(UUID employeeId, String toStatus, String reason, boolean emergency, String status, UUID requestedBy, String reviewOutcome) {}

    private Row lock(TenantId tenantId, UUID requestId) {
        List<Row> rows = jdbc.query("""
                SELECT employee_id, to_status, reason, emergency, status, requested_by, review_outcome
                  FROM hr_status_change_requests WHERE tenant_id = ? AND id = ? FOR UPDATE""",
                (rs, i) -> new Row(rs.getObject("employee_id", UUID.class), rs.getString("to_status"), rs.getString("reason"), rs.getBoolean("emergency"),
                        rs.getString("status"), rs.getObject("requested_by", UUID.class), rs.getString("review_outcome")),
                tenantId.getValue(), requestId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Status change request", requestId.toString());
        return rows.get(0);
    }

    private void apply(HrEmployee emp, String toStatus) {
        if (EmployeeStatusRules.SUSPENDED.equals(toStatus)) emp.suspend(); else emp.reinstate();
        employeeRepo.save(emp);
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
