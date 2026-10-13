package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.application.internal.AllocationService.CollectorLoadView;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorProfile;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Profile plus live load. utilisationPercent is open cases ÷ maxOpenCases (null when there is no case limit). */
public record CollectorResponse(
        UUID userId, String userName, String team, CollectorTier tier, Set<String> skills, Integer maxOpenCases,
        BigDecimal maxCaseValue, BigDecimal maxPortfolioValue, LocalDate awayUntil, boolean active, Instant lastAssignedAt,
        Integer openCases, BigDecimal portfolioValue, Long openWorkItems, Long overdueWorkItems, Integer utilisationPercent
) {
    public static CollectorResponse of(CollectorProfile p) {
        return new CollectorResponse(p.getUserId(), p.getUserName(), p.getTeam(), p.getTier(), p.getSkills(), p.getMaxOpenCases(),
                p.getMaxCaseValue(), p.getMaxPortfolioValue(), p.getAwayUntil(), p.isActive(), p.getLastAssignedAt(),
                null, null, null, null, null);
    }

    public static CollectorResponse of(CollectorLoadView v) {
        CollectorProfile p = v.profile();
        return new CollectorResponse(p.getUserId(), p.getUserName(), p.getTeam(), p.getTier(), p.getSkills(), p.getMaxOpenCases(),
                p.getMaxCaseValue(), p.getMaxPortfolioValue(), p.getAwayUntil(), p.isActive(), p.getLastAssignedAt(),
                v.openCases(), v.portfolioValue(), v.openWorkItems(), v.overdueWorkItems(), v.utilisationPercent());
    }
}
