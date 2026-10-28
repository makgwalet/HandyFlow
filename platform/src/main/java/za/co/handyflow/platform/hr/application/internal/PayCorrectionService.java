package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunEventRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayslipRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Corrects a payslip that was issued wrongly without touching it. The correction is a pay item (back pay or a recovery) linked to the
 * payslip, which the next pay run pays or takes from net pay. It is written to the history of the run that made the mistake, so anyone
 * reading that run sees it was corrected, by whom and why.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCorrectionService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrPayslipRepository payslipRepo;
    private final HrPayRunRepository payRunRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrPayRunEventRepository eventRepo;
    private final PayAdjustmentService payAdjustments;

    public record EnterRequest(String direction, BigDecimal amount, String reason) {}

    @Transactional(readOnly = true)
    public List<PayAdjustmentDtos.View> forPayslip(TenantId tenantId, UUID payslipId) {
        load(tenantId, payslipId);
        return payAdjustments.forPayslip(tenantId, payslipId);
    }

    @Transactional
    public PayAdjustmentDtos.View enter(TenantId tenantId, UUID payslipId, EnterRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrPayslip slip = load(tenantId, payslipId);
        HrPayRun run = payRunRepo.findByTenantAndId(tenantId, slip.getPayRunId()).orElseThrow(() -> new ResourceNotFoundException("PayRun", slip.getPayRunId().toString()));
        HrEmployee emp = employeeRepo.findActiveById(tenantId, slip.getEmployeeId()).orElseThrow(() -> new ResourceNotFoundException("Employee", slip.getEmployeeId().toString()));
        String direction = req == null ? null : req.direction();
        String kind = PayCorrectionRules.kindFor(direction);
        boolean waiting = kind != null && payAdjustments.correctionWaiting(tenantId, payslipId, kind);
        String problem = PayCorrectionRules.problem(direction, req == null ? null : req.amount(), slip.getNetPay(), run.getStatus(), emp.getStatus(), waiting);
        if (problem != null) throw new IllegalArgumentException(problem);
        LocalDate today = LocalDate.now(ZONE);
        boolean back = PayAdjustmentRules.BACK_PAY.equals(kind);
        PayAdjustmentDtos.View view = payAdjustments.add(tenantId, emp.getId(), new PayAdjustmentDtos.AddRequest(kind, req.amount().setScale(2, RoundingMode.HALF_UP), today,
                PayCorrectionRules.description(run.getPayRunNumber()),
                back ? PayCorrectionRules.relatesFrom(run.getPeriodStart(), emp.getStartDate()) : null,
                back ? PayCorrectionRules.relatesTo(run.getPeriodEnd(), today) : null, req.reason()), actor, payslipId);
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), run.getId(), "PAYSLIP_CORRECTION_ENTERED", run.getStatus(), run.getStatus(), actor,
                emp.getEmployeeNumber() + ": " + (back ? "underpaid, back pay " : "overpaid, recovery ") + view.amount().toPlainString()));
        log.info("Correction {} {} entered for payslip={} run={} by {}", kind, view.amount(), payslipId, run.getPayRunNumber(), actor);
        return view;
    }

    private HrPayslip load(TenantId tenantId, UUID payslipId) {
        return payslipRepo.findByTenantAndId(tenantId, payslipId).orElseThrow(() -> new ResourceNotFoundException("Payslip", payslipId.toString()));
    }
}
