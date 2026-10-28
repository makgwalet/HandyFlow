package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.LeaveAdvanceRules;
import za.co.handyflow.platform.hr.domain.model.PayAdjustmentRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Annual leave an employee took before earning it, found when employment ends. The days and the amount are worked out and shown; taking it
 * back from final pay happens only when someone with payroll rights names the employee's written agreement. It is then entered as an
 * ordinary recovery, so it is taken from net pay by the final run, in the order entered, and only if net pay covers it.
 */
@Service
@RequiredArgsConstructor
public class LeaveAdvanceService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrEmployeeRepository employeeRepo;
    private final LeaveService leaveService;
    private final PayAdjustmentService payAdjustments;

    /** What was taken in advance, what it comes to, and whether a recovery has already been entered. */
    public record Position(boolean terminated, BigDecimal days, BigDecimal amount, boolean alreadyEntered) {}

    public record RecoverRequest(String agreementRef, LocalDate agreementDate) {}

    @Transactional
    public Position position(TenantId tenantId, UUID employeeId) {
        HrEmployee emp = employee(tenantId, employeeId);
        if (!"TERMINATED".equals(emp.getStatus()) || emp.getEndDate() == null) return new Position(false, BigDecimal.ZERO, BigDecimal.ZERO, false);
        BigDecimal days = leaveService.annualLeaveAdvance(tenantId, emp, emp.getEndDate());
        return new Position(true, days, LeaveAdvanceRules.amount(emp.getGrossSalary(), days), alreadyEntered(tenantId, employeeId));
    }

    @Transactional
    public PayAdjustmentDtos.View recover(TenantId tenantId, UUID employeeId, RecoverRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employee(tenantId, employeeId);
        BigDecimal days = "TERMINATED".equals(emp.getStatus()) && emp.getEndDate() != null
                ? leaveService.annualLeaveAdvance(tenantId, emp, emp.getEndDate()) : BigDecimal.ZERO;
        String problem = LeaveAdvanceRules.problem(emp.getStatus(), days, alreadyEntered(tenantId, employeeId),
                req == null ? null : req.agreementRef(), req == null ? null : req.agreementDate(), LocalDate.now(ZONE));
        if (problem != null) throw new IllegalArgumentException(problem);
        BigDecimal amount = LeaveAdvanceRules.amount(emp.getGrossSalary(), days);
        if (amount.signum() <= 0) throw new IllegalArgumentException("No annual leave was taken in advance.");
        return payAdjustments.add(tenantId, employeeId, new PayAdjustmentDtos.AddRequest(PayAdjustmentRules.RECOVERY, amount, emp.getEndDate(),
                LeaveAdvanceRules.DESCRIPTION, null, null, LeaveAdvanceRules.reason(days, req.agreementRef(), req.agreementDate())), actor);
    }

    private boolean alreadyEntered(TenantId tenantId, UUID employeeId) {
        return payAdjustments.forEmployee(tenantId, employeeId).stream().anyMatch(v ->
                PayAdjustmentRules.RECOVERY.equals(v.kind()) && LeaveAdvanceRules.DESCRIPTION.equals(v.description()) && PayAdjustmentRules.OPEN.equals(v.status()));
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }
}
