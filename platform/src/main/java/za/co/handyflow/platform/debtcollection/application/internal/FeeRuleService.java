package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.FeeCalcMethod;
import za.co.handyflow.platform.debtcollection.domain.model.FeeRule;
import za.co.handyflow.platform.debtcollection.domain.repository.FeeRuleRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Versioned fee rules: draft (new version) → approve (four-eyes; supersedes the previous approved version) → retire. */
@Service
@RequiredArgsConstructor
public class FeeRuleService {

    private final FeeRuleRepository repository;

    @Transactional
    public FeeRule draft(TenantId tenantId, String code, String description, FeeCalcMethod method, BigDecimal fixedAmount,
                         BigDecimal percent, BigDecimal maxPerCase, Integer maxChargesPerMonth, LocalDate effectiveFrom,
                         LocalDate effectiveTo, String legalBasisNote, UUID actorId) {
        String normalized = code == null ? null : code.trim().toUpperCase();
        int next = normalized == null ? 1 : repository.maxVersion(tenantId, normalized) + 1;
        return repository.save(FeeRule.draft(tenantId, normalized, next, description, method, fixedAmount, percent,
                maxPerCase, maxChargesPerMonth, effectiveFrom, effectiveTo, legalBasisNote, actorId));
    }

    @Transactional
    public FeeRule approve(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        FeeRule rule = find(tenantId, id);
        // Validate (status + four-eyes) before touching the currently approved version.
        FeeRule previous = repository.findApprovedByCode(tenantId, rule.getCode()).orElse(null);
        rule.approve(actorId, actorName);
        if (previous != null && !previous.getId().equals(rule.getId())) {
            previous.supersede();
            repository.saveAndFlush(previous); // free the "one APPROVED per code" slot before the new row is flushed
        }
        return repository.save(rule);
    }

    @Transactional
    public FeeRule retire(TenantId tenantId, UUID id, String reason) {
        FeeRule rule = find(tenantId, id);
        rule.retire(reason);
        return repository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<FeeRule> listAll(TenantId tenantId) {
        return repository.findAllForTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<FeeRule> listApproved(TenantId tenantId) {
        return repository.findAllApproved(tenantId);
    }

    private FeeRule find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FeeRule", id.toString()));
    }
}
