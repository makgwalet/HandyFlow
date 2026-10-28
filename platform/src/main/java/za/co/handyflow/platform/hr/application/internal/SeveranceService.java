package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.NoticeRules;
import za.co.handyflow.platform.hr.domain.model.PayAdjustmentRules;
import za.co.handyflow.platform.hr.domain.model.SeveranceRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Severance for someone retrenched. Shows the years served and the BCEA minimum, then enters the severance as a pay item (kind SEVERANCE)
 * with the SARS directive number and the tax the directive asks for. {@link PayAdjustmentService#addSeverance} applies the rules.
 */
@Service
@RequiredArgsConstructor
public class SeveranceService {

    private final HrEmployeeRepository employeeRepo;
    private final PayAdjustmentService payAdjustments;

    public record Position(boolean retrenched, String endType, int completedYears, BigDecimal weeklyPay, BigDecimal minimum, String summary,
                           boolean alreadyEntered) {}

    @Transactional(readOnly = true)
    public Position position(TenantId tenantId, UUID employeeId) {
        HrEmployee e = employee(tenantId, employeeId);
        if (!"TERMINATED".equals(e.getStatus()) || e.getEndDate() == null || !NoticeRules.RETRENCHMENT.equals(e.getEndType()))
            return new Position(false, e.getEndType(), 0, BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        return new Position(true, e.getEndType(), SeveranceRules.completedYears(e.getStartDate(), e.getEndDate()), SeveranceRules.weeklyPay(e.getGrossSalary()),
                SeveranceRules.statutoryMinimum(e.getGrossSalary(), e.getStartDate(), e.getEndDate()),
                SeveranceRules.summary(e.getEndType(), e.getGrossSalary(), e.getStartDate(), e.getEndDate()),
                payAdjustments.forEmployee(tenantId, employeeId).stream().anyMatch(v -> PayAdjustmentRules.SEVERANCE.equals(v.kind()) && PayAdjustmentRules.OPEN.equals(v.status())));
    }

    @Transactional
    public PayAdjustmentDtos.View enter(TenantId tenantId, UUID employeeId, PayAdjustmentService.SeveranceRequest req, UUID actor) {
        if (req == null) throw new IllegalArgumentException("Enter the severance amount.");
        return payAdjustments.addSeverance(tenantId, employeeId, req, actor);
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }
}
