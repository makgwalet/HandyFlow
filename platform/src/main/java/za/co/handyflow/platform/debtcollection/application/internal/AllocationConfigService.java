package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationRule;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationSettings;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;
import za.co.handyflow.platform.debtcollection.domain.repository.AllocationRuleRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.AllocationSettingsRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Routing rules and the per-tenant allocation switch. Rules are deactivated, never deleted, so a past plan stays explainable. */
@Service
@RequiredArgsConstructor
public class AllocationConfigService {

    private final AllocationRuleRepository ruleRepository;
    private final AllocationSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public AllocationSettings settings(TenantId tenantId) {
        return settingsRepository.findForTenant(tenantId).orElseGet(() -> AllocationSettings.defaults(tenantId));
    }

    @Transactional
    public AllocationSettings updateSettings(TenantId tenantId, boolean enabled, AllocationStrategy defaultStrategy,
                                             boolean reallocateUnavailable) {
        AllocationSettings s = settingsRepository.findForTenant(tenantId).orElseGet(() -> AllocationSettings.defaults(tenantId));
        s.update(enabled, defaultStrategy, reallocateUnavailable);
        return settingsRepository.save(s);
    }

    @Transactional
    public AllocationRule createRule(TenantId tenantId, String name, int order, BigDecimal minBalance, BigDecimal maxBalance,
                                     Integer minAgeDays, Integer maxAgeDays, Set<CaseStatus> statuses, String team,
                                     String skill, CollectorTier minTier, AllocationStrategy strategy) {
        return ruleRepository.save(AllocationRule.create(tenantId, name, order, minBalance, maxBalance, minAgeDays, maxAgeDays,
                statuses, team, skill, minTier, strategy));
    }

    @Transactional
    public AllocationRule updateRule(TenantId tenantId, UUID id, String name, int order, BigDecimal minBalance,
                                     BigDecimal maxBalance, Integer minAgeDays, Integer maxAgeDays, Set<CaseStatus> statuses,
                                     String team, String skill, CollectorTier minTier, AllocationStrategy strategy) {
        AllocationRule r = find(tenantId, id);
        r.update(name, order, minBalance, maxBalance, minAgeDays, maxAgeDays, statuses, team, skill, minTier, strategy);
        return ruleRepository.save(r);
    }

    @Transactional
    public AllocationRule setRuleActive(TenantId tenantId, UUID id, boolean active) {
        AllocationRule r = find(tenantId, id);
        if (active) r.activate(); else r.deactivate();
        return ruleRepository.save(r);
    }

    @Transactional(readOnly = true)
    public List<AllocationRule> listRules(TenantId tenantId) {
        return ruleRepository.findAllForTenant(tenantId);
    }

    private AllocationRule find(TenantId tenantId, UUID id) {
        return ruleRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("AllocationRule", id.toString()));
    }
}
