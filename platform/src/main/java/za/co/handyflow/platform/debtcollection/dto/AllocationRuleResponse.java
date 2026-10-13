package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.AllocationRule;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record AllocationRuleResponse(UUID id, String name, int ruleOrder, boolean active, BigDecimal minBalance,
                                     BigDecimal maxBalance, Integer minAgeDays, Integer maxAgeDays, Set<CaseStatus> statuses,
                                     String requiredTeam, String requiredSkill, CollectorTier minTier,
                                     AllocationStrategy strategy) {
    public static AllocationRuleResponse of(AllocationRule r) {
        return new AllocationRuleResponse(r.getId(), r.getName(), r.getRuleOrder(), r.isActive(), r.getMinBalance(),
                r.getMaxBalance(), r.getMinAgeDays(), r.getMaxAgeDays(), r.statuses(), r.getRequiredTeam(),
                r.getRequiredSkill(), r.getMinTier(), r.getStrategy());
    }
}
