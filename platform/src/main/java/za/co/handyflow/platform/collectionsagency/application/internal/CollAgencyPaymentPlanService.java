package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPaymentPlan;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyPaymentPlanRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollAgencyPaymentPlanService {

    private final CollAgencyPaymentPlanRepository repository;
    private final CollAgencyDebtorAccountService debtorAccountService;

    @Transactional
    public CollAgencyPaymentPlan propose(TenantId tenantId, UUID debtorAccountId, BigDecimal totalAgreedAmount,
                                          BigDecimal installmentAmount, String frequency, LocalDate startDate,
                                          Integer numberOfInstallments, String notes) {
        debtorAccountService.findActive(tenantId, debtorAccountId); // 404s if the account doesn't exist or isn't this tenant's
        CollAgencyPaymentPlan plan = CollAgencyPaymentPlan.propose(tenantId.getValue(), debtorAccountId,
                totalAgreedAmount, installmentAmount, frequency, startDate, numberOfInstallments, notes);
        plan = repository.save(plan);
        debtorAccountService.advanceStatus(tenantId, debtorAccountId, "PAYMENT_PLAN_ACTIVE");
        return plan;
    }

    @Transactional
    public CollAgencyPaymentPlan markInstallmentPaid(TenantId tenantId, UUID id) {
        CollAgencyPaymentPlan plan = findActive(tenantId, id);
        plan.markInstallmentPaid();
        return repository.save(plan);
    }

    @Transactional
    public CollAgencyPaymentPlan markDefaulted(TenantId tenantId, UUID id, String reason) {
        CollAgencyPaymentPlan plan = findActive(tenantId, id);
        plan.markDefaulted(reason);
        return repository.save(plan);
    }

    @Transactional
    public CollAgencyPaymentPlan cancel(TenantId tenantId, UUID id, String reason) {
        CollAgencyPaymentPlan plan = findActive(tenantId, id);
        plan.cancel(reason);
        return repository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyPaymentPlan> listForDebtorAccount(TenantId tenantId, UUID debtorAccountId) {
        return repository.findByDebtorAccount(tenantId.getValue(), debtorAccountId);
    }

    @Transactional(readOnly = true)
    public CollAgencyPaymentPlan get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    private CollAgencyPaymentPlan findActive(TenantId tenantId, UUID id) {
        return repository.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("CollAgencyPaymentPlan", id.toString()));
    }
}
