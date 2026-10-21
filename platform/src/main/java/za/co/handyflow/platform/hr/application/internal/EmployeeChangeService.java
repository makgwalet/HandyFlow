package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeChangeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayrollSettingsRepository;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Changes to pay and bank details go through a request, a decision by someone else, and an effective date. Until the
 * date arrives the employee row is untouched; payroll applies due changes just before it calculates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeChangeService {

    private final HrEmployeeChangeRepository changeRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrPayrollSettingsRepository settingsRepo;
    private final EmployeeAuditService audit;

    @Transactional
    public EmployeeChangeResponse requestCompensation(TenantId tenantId, UUID employeeId, CompensationChangeRequest req, UUID actor) {
        HrEmployee emp = employee(tenantId, employeeId);
        refuseIfTerminated(emp);
        LocalDate today = LocalDate.now();
        BigDecimal gross = req.grossSalary() != null ? req.grossSalary() : emp.getGrossSalary();
        BigDecimal travel = req.travelAllowance() != null ? req.travelAllowance() : emp.getTravelAllowance();
        BigDecimal medical = req.medicalAidContribution() != null ? req.medicalAidContribution() : emp.getMedicalAidContribution();
        BigDecimal pension = req.pensionContribution() != null ? req.pensionContribution() : emp.getPensionContribution();
        String frequency = req.payFrequency() != null && !req.payFrequency().isBlank() ? req.payFrequency() : emp.getPayFrequency();
        fail(EmployeeChangeRules.compensationProblem(gross, travel, medical, pension, frequency));
        fail(EmployeeChangeRules.effectiveProblem(req.effectiveFrom(), emp.getStartDate(), today));
        fail(EmployeeChangeRules.reasonProblem(req.reason()));

        Map<String, String> before = compensation(emp.getGrossSalary(), emp.getTravelAllowance(), emp.getMedicalAidContribution(),
                emp.getPensionContribution(), emp.getPayFrequency());
        Map<String, String> after = compensation(gross, travel, medical, pension, frequency);
        return open(tenantId, emp, EmployeeChangeRules.COMPENSATION, before, after, req.effectiveFrom(), req.reason(), actor);
    }

    @Transactional
    public EmployeeChangeResponse requestBank(TenantId tenantId, UUID employeeId, BankChangeRequest req, UUID actor) {
        HrEmployee emp = employee(tenantId, employeeId);
        refuseIfTerminated(emp);
        fail(EmployeeRules.bankProblem(req.bankName(), req.accountNumber(), req.branchCode()));
        fail(EmployeeChangeRules.effectiveProblem(req.effectiveFrom(), emp.getStartDate(), LocalDate.now()));
        fail(EmployeeChangeRules.reasonProblem(req.reason()));

        Map<String, String> before = bank(emp.getBankName(), emp.getBankAccountNumber(), emp.getBankBranchCode());
        Map<String, String> after = bank(req.bankName().trim(), req.accountNumber().replaceAll("\\s", ""), req.branchCode().replaceAll("\\s", ""));
        return open(tenantId, emp, EmployeeChangeRules.BANK_DETAILS, before, after, req.effectiveFrom(), req.reason(), actor);
    }

    @Transactional
    public EmployeeChangeResponse approve(TenantId tenantId, UUID changeId, String note, UUID actor) {
        HrEmployeeChange c = change(tenantId, changeId);
        boolean self = EmployeeChangeRules.checkCanDecide(c.getStatus(), c.getRequestedBy(), actor, allowSelfApproval(tenantId));
        c.approve(actor, note, self);
        changeRepo.save(c);
        audit.record(tenantId, c.getEmployeeId(), self ? "CHANGE_APPROVED_OWN" : "CHANGE_APPROVED", summary(c), actor);
        // A change dated today or earlier takes effect now rather than waiting for the next payroll.
        if (EmployeeChangeRules.isDue(c.getStatus(), c.getEffectiveFrom(), LocalDate.now())) apply(tenantId, c, actor);
        return toResponse(c, nameOf(tenantId, c.getEmployeeId()));
    }

    @Transactional
    public EmployeeChangeResponse reject(TenantId tenantId, UUID changeId, String note, UUID actor) {
        HrEmployeeChange c = change(tenantId, changeId);
        EmployeeChangeRules.checkCanReject(c.getStatus(), note);
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        c.reject(actor, note);
        changeRepo.save(c);
        audit.record(tenantId, c.getEmployeeId(), "CHANGE_REJECTED", summary(c), actor);
        return toResponse(c, nameOf(tenantId, c.getEmployeeId()));
    }

    @Transactional
    public EmployeeChangeResponse cancel(TenantId tenantId, UUID changeId, UUID actor) {
        HrEmployeeChange c = change(tenantId, changeId);
        EmployeeChangeRules.checkCanCancel(c.getStatus(), c.getRequestedBy(), actor);
        c.cancel(actor);
        changeRepo.save(c);
        audit.record(tenantId, c.getEmployeeId(), "CHANGE_CANCELLED", summary(c), actor);
        return toResponse(c, nameOf(tenantId, c.getEmployeeId()));
    }

    /**
     * Applies approved changes whose date has come. Called before payroll calculates, so a run never uses pay that was
     * agreed to change. Returns how many were applied.
     */
    @Transactional
    public int applyDue(TenantId tenantId, LocalDate today) {
        int n = 0;
        for (HrEmployeeChange c : changeRepo.due(tenantId, today)) {
            apply(tenantId, c, null);
            n++;
        }
        return n;
    }

    @Transactional(readOnly = true)
    public List<EmployeeChangeResponse> pending(TenantId tenantId) {
        Map<UUID, String> names = new HashMap<>();
        return changeRepo.pending(tenantId).stream()
                .map(c -> toResponse(c, names.computeIfAbsent(c.getEmployeeId(), id -> nameOf(tenantId, id)))).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeChangeResponse> forEmployee(TenantId tenantId, UUID employeeId) {
        String name = employee(tenantId, employeeId).getFullName();
        return changeRepo.forEmployee(tenantId, employeeId).stream().map(c -> toResponse(c, name)).toList();
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────────

    private void apply(TenantId tenantId, HrEmployeeChange c, UUID actor) {
        HrEmployee emp = employee(tenantId, c.getEmployeeId());
        Map<String, String> v = c.getNewValues();
        if (EmployeeChangeRules.COMPENSATION.equals(c.getChangeType())) {
            emp.applyCompensation(dec(v.get(EmployeeChangeRules.GROSS)), dec(v.get(EmployeeChangeRules.TRAVEL)),
                    dec(v.get(EmployeeChangeRules.MEDICAL)), dec(v.get(EmployeeChangeRules.PENSION)), v.get(EmployeeChangeRules.FREQUENCY));
        } else {
            emp.applyBank(v.get(EmployeeChangeRules.BANK_NAME), v.get(EmployeeChangeRules.ACCOUNT), v.get(EmployeeChangeRules.BRANCH));
        }
        employeeRepo.save(emp);
        c.markApplied();
        changeRepo.save(c);
        audit.record(tenantId, emp.getId(), "CHANGE_APPLIED", summary(c), actor);
        log.info("Applied {} change {} to employee={}", c.getChangeType(), c.getId(), emp.getEmployeeNumber());
    }

    private EmployeeChangeResponse open(TenantId tenantId, HrEmployee emp, String type, Map<String, String> before,
                                        Map<String, String> after, LocalDate effective, String reason, UUID actor) {
        if (EmployeeChangeRules.changedFields(before, after).isEmpty()) {
            throw new IllegalArgumentException("Nothing is different from what is on the record.");
        }
        if (changeRepo.countPending(tenantId, emp.getId(), type) > 0) {
            throw new IllegalStateException("There is already a change of this kind waiting for a decision for this employee. Cancel it or have it decided first.");
        }
        HrEmployeeChange c = HrEmployeeChange.request(tenantId.getValue(), emp.getId(), type, before, after, effective, reason, actor);
        changeRepo.save(c);
        audit.record(tenantId, emp.getId(), "CHANGE_REQUESTED", summary(c), actor);
        return toResponse(c, emp.getFullName());
    }

    private static Map<String, String> compensation(BigDecimal gross, BigDecimal travel, BigDecimal medical, BigDecimal pension, String frequency) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(EmployeeChangeRules.GROSS, str(gross));
        m.put(EmployeeChangeRules.TRAVEL, str(travel));
        m.put(EmployeeChangeRules.MEDICAL, str(medical));
        m.put(EmployeeChangeRules.PENSION, str(pension));
        m.put(EmployeeChangeRules.FREQUENCY, frequency);
        return m;
    }

    private static Map<String, String> bank(String name, String account, String branch) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(EmployeeChangeRules.BANK_NAME, name);
        m.put(EmployeeChangeRules.ACCOUNT, account);
        m.put(EmployeeChangeRules.BRANCH, branch);
        return m;
    }

    /** Names the fields, never the values: the audit trail is read by people who cannot see pay. */
    private static String summary(HrEmployeeChange c) {
        List<String> labels = EmployeeChangeRules.changedFields(c.getOldValues(), c.getNewValues()).stream().map(EmployeeChangeRules::label).toList();
        return String.join(", ", labels) + " effective " + c.getEffectiveFrom();
    }

    private EmployeeChangeResponse toResponse(HrEmployeeChange c, String employeeName) {
        List<EmployeeChangeResponse.Field> fields = new ArrayList<>();
        for (String key : EmployeeChangeRules.changedFields(c.getOldValues(), c.getNewValues())) {
            fields.add(new EmployeeChangeResponse.Field(key, EmployeeChangeRules.label(key),
                    show(key, c.getOldValues().get(key)), show(key, c.getNewValues().get(key))));
        }
        return new EmployeeChangeResponse(c.getId(), c.getEmployeeId(), employeeName, c.getChangeType(), fields, c.getEffectiveFrom(),
                c.getReason(), c.getStatus(), c.getRequestedBy(), c.getRequestedAt(), c.getDecidedBy(), c.getDecidedAt(),
                c.getDecisionNote(), c.isSelfApproved(), c.getAppliedAt());
    }

    private static String show(String key, String value) {
        return EmployeeChangeRules.ACCOUNT.equals(key) ? EmployeeRules.maskAccount(value) : value;
    }

    private static String str(BigDecimal v) { return v == null ? "0" : v.stripTrailingZeros().toPlainString(); }
    private static BigDecimal dec(String s) { return s == null || s.isBlank() ? BigDecimal.ZERO : new BigDecimal(s); }

    private static void fail(String problem) {
        if (problem != null) throw new IllegalArgumentException(problem);
    }

    private static void refuseIfTerminated(HrEmployee emp) {
        if ("TERMINATED".equals(emp.getStatus())) throw new IllegalStateException("This employee has been terminated; pay and bank details can no longer change.");
    }

    private boolean allowSelfApproval(TenantId tenantId) {
        return settingsRepo.findById(tenantId.getValue()).map(HrPayrollSettings::isAllowSelfApproval).orElse(false);
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private HrEmployeeChange change(TenantId tenantId, UUID id) {
        return changeRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee change", id.toString()));
    }

    private String nameOf(TenantId tenantId, UUID employeeId) {
        return employeeRepo.findActiveById(tenantId, employeeId).map(HrEmployee::getFullName).orElse("Unknown");
    }
}
