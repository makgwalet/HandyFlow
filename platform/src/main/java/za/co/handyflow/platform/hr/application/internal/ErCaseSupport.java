package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** What every employee relations service needs: finding a case the person may see, the checks that apply to all of them, the timeline. */
@Component
@RequiredArgsConstructor
class ErCaseSupport {

    private final HrErCaseRepository caseRepo;
    private final HrErStepRepository stepRepo;
    private final HrErEventRepository eventRepo;
    private final HrEmployeeRepository employeeRepo;
    private final JdbcTemplate jdbc;

    LocalDate today() { return LocalDate.now(DisciplineRules.ZONE); }

    /** A case the person may see. A confidential case they may not see looks the same as one that does not exist. */
    HrErCase find(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrErCase c = caseRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Case", String.valueOf(id)));
        if (!ErRules.canSee(held, userId, c.isConfidential(), c.getOwnerUserId())) throw new ResourceNotFoundException("Case", String.valueOf(id));
        return c;
    }

    /** A case to act on. Nobody works a case about themselves. */
    HrErCase findToAct(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrErCase c = find(tenantId, id, held, userId);
        if (userId != null) {
            employeeRepo.findByUserId(tenantId, userId).ifPresent(e -> {
                if (e.getId().equals(c.getEmployeeId())) throw new AccessDeniedException("You cannot work on a case about yourself.");
            });
        }
        return c;
    }

    HrEmployee myEmployee(TenantId tenantId, UUID userId) {
        if (userId == null) throw new AccessDeniedException("Your user could not be identified.");
        return employeeRepo.findByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
    }

    HrEmployee employee(TenantId tenantId, UUID id) {
        if (id == null) throw new IllegalArgumentException("Choose the employee.");
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    String nameOf(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        return employeeRepo.findActiveById(tenantId, employeeId).map(HrEmployee::getFullName).orElse("Unknown");
    }

    /** The next number for this company and year, e.g. ER-2026-0007. Safe when two cases are opened at once. */
    String nextNumber(TenantId tenantId) {
        int year = today().getYear();
        Integer n = jdbc.queryForObject(
                "INSERT INTO hr_er_counters (tenant_id, year, last_no) VALUES (?, ?, 1) "
                        + "ON CONFLICT (tenant_id, year) DO UPDATE SET last_no = hr_er_counters.last_no + 1 RETURNING last_no",
                Integer.class, tenantId.getValue(), year);
        return String.format("ER-%d-%04d", year, n == null ? 1 : n);
    }

    void event(HrErCase c, String action, String note, boolean visibleToEmployee, UUID by) {
        eventRepo.save(HrErEvent.of(c.getTenantId(), c.getId(), action, note, visibleToEmployee, by));
    }

    Set<String> doneSteps(HrErCase c) {
        return stepRepo.forCase(c.getId()).stream().filter(HrErStep::isDone).map(HrErStep::getCode).collect(Collectors.toSet());
    }

    void move(HrErCase c, String to) {
        if (!ErRules.canMove(c.getCaseType(), c.getStatus(), to))
            throw new IllegalStateException("A case that is " + words(c.getStatus()) + " cannot go to " + words(to) + ".");
    }

    static String words(String status) { return status == null ? "" : status.toLowerCase().replace('_', ' '); }

    static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }

    static void requireManage(Set<String> held) {
        if (!ErRules.canManage(held)) throw new AccessDeniedException("You do not have permission to run employee relations cases.");
    }

    static void requireDecide(Set<String> held) {
        if (!ErRules.canDecide(held)) throw new AccessDeniedException("You do not have permission to decide employee relations cases.");
    }

    static void requireType(HrErCase c, String type) {
        if (!type.equals(c.getCaseType())) throw new IllegalStateException("That step does not apply to this kind of case.");
    }
}
