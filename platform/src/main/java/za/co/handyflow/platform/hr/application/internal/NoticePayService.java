package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.NoticePayRules;
import za.co.handyflow.platform.hr.domain.model.NoticeRules;
import za.co.handyflow.platform.hr.domain.model.PayAdjustmentRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Pay in place of notice for someone the employer let go with notice owed. The days required, given and short are worked out and shown, and
 * the amount owed is offered; entering it makes an ordinary pay item (kind NOTICE_PAY) dated on the last day, which the final run pays and
 * taxes as an annual payment. {@link PayAdjustmentService#add} applies the rules, so this is the same whichever way it is entered.
 */
@Service
@RequiredArgsConstructor
public class NoticePayService {

    private final HrEmployeeRepository employeeRepo;
    private final PayAdjustmentService payAdjustments;

    public record Position(boolean terminated, String endType, String endLabel, int requiredDays, int givenDays, int shortfallDays,
                           BigDecimal owed, BigDecimal cap, boolean alreadyEntered) {}

    public record EnterRequest(BigDecimal amount, String reason) {}

    @Transactional(readOnly = true)
    public Position position(TenantId tenantId, UUID employeeId) {
        HrEmployee e = employee(tenantId, employeeId);
        if (!"TERMINATED".equals(e.getStatus()) || e.getEndDate() == null)
            return new Position(false, null, null, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, false);
        return new Position(true, e.getEndType(), NoticeRules.typeLabel(e.getEndType()),
                NoticeRules.requiredDays(e.getStartDate(), e.getNoticeGivenOn(), e.getEndDate()),
                NoticeRules.givenDays(e.getNoticeGivenOn(), e.getEndDate()),
                NoticeRules.shortfallDays(e.getEndType(), e.getStartDate(), e.getNoticeGivenOn(), e.getEndDate()),
                NoticeRules.owedPay(e.getEndType(), e.getGrossSalary(), e.getStartDate(), e.getNoticeGivenOn(), e.getEndDate()),
                NoticeRules.fullPay(e.getEndType(), e.getGrossSalary(), e.getStartDate(), e.getNoticeGivenOn(), e.getEndDate()),
                payAdjustments.forEmployee(tenantId, employeeId).stream().anyMatch(v -> PayAdjustmentRules.NOTICE_PAY.equals(v.kind()) && PayAdjustmentRules.OPEN.equals(v.status())));
    }

    /** An amount left blank is the pay owed for the days short. */
    @Transactional
    public PayAdjustmentDtos.View enter(TenantId tenantId, UUID employeeId, EnterRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee e = employee(tenantId, employeeId);
        if (!"TERMINATED".equals(e.getStatus()) || e.getEndDate() == null) throw new IllegalArgumentException("Notice pay is entered once employment has ended.");
        BigDecimal amount = req != null && req.amount() != null ? req.amount()
                : NoticeRules.owedPay(e.getEndType(), e.getGrossSalary(), e.getStartDate(), e.getNoticeGivenOn(), e.getEndDate());
        return payAdjustments.add(tenantId, employeeId, new PayAdjustmentDtos.AddRequest(PayAdjustmentRules.NOTICE_PAY,
                amount.setScale(2, RoundingMode.HALF_UP), e.getEndDate(), NoticePayRules.DESCRIPTION, null, null, req == null ? null : req.reason()), actor);
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }
}
