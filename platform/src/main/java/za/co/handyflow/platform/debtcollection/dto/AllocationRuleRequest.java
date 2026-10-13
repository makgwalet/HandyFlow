package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Conditions (all optional, all must match): balance range, case age in days, statuses. Requirements for the collector
 * (all optional): team, skill, minimum tier. Rules are tried in ruleOrder, lowest first; the first match wins.
 */
public record AllocationRuleRequest(
        @NotBlank String name,
        @PositiveOrZero int ruleOrder,
        BigDecimal minBalance,
        BigDecimal maxBalance,
        @PositiveOrZero Integer minAgeDays,
        @PositiveOrZero Integer maxAgeDays,
        Set<CaseStatus> statuses,
        String requiredTeam,
        String requiredSkill,
        CollectorTier minTier,
        AllocationStrategy strategy
) {}
