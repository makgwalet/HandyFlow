package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanFrequency;
import za.co.handyflow.platform.debtcollection.domain.repository.PaymentPlanRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Manages PaymentPlan agreements against a case. Proposing a plan also
 * advances the case's own status to PAYMENT_PLAN_ACTIVE (a plan without the
 * case reflecting it would be a silent inconsistency) — the reverse
 * transitions (markDefaulted/cancel) deliberately do NOT auto-move the case
 * status, since a defaulted or cancelled plan doesn't dictate what staff
 * should do next (resume demands? write off? hand to legal?) — that stays
 * an explicit advanceStatus()/writeOff() call.
 */
@Service
@RequiredArgsConstructor
public class PaymentPlanService {

    private final PaymentPlanRepository repository;
    private final DebtCollectionCaseService caseService;

    @Transactional
    public PaymentPlan propose(TenantId tenantId, UUID caseId, BigDecimal totalAgreedAmount,
                                BigDecimal installmentAmount, PaymentPlanFrequency frequency, LocalDate startDate,
                                Integer numberOfInstallments, String notes, UUID createdBy) {
        caseService.get(tenantId, caseId); // 404s if the case doesn't exist or isn't this tenant's
        PaymentPlan plan = PaymentPlan.propose(tenantId, caseId, totalAgreedAmount, installmentAmount, frequency,
                startDate, numberOfInstallments, notes, createdBy);
        plan = repository.save(plan);
        caseService.advanceStatus(tenantId, caseId, CaseStatus.PAYMENT_PLAN_ACTIVE);
        return plan;
    }

    @Transactional
    public PaymentPlan markInstallmentPaid(TenantId tenantId, UUID id) {
        PaymentPlan plan = findActive(tenantId, id);
        plan.markInstallmentPaid();
        return repository.save(plan);
    }

    @Transactional
    public PaymentPlan markDefaulted(TenantId tenantId, UUID id, String reason) {
        PaymentPlan plan = findActive(tenantId, id);
        plan.markDefaulted(reason);
        return repository.save(plan);
    }

    @Transactional
    public PaymentPlan cancel(TenantId tenantId, UUID id, String reason) {
        PaymentPlan plan = findActive(tenantId, id);
        plan.cancel(reason);
        return repository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<PaymentPlan> listForCase(TenantId tenantId, UUID caseId) {
        return repository.findByCaseId(tenantId, caseId);
    }

    @Transactional(readOnly = true)
    public PaymentPlan get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    private PaymentPlan findActive(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentPlan", id.toString()));
    }
}
