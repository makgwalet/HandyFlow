package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.FeeCalcMethod;
import za.co.handyflow.platform.debtcollection.domain.model.FeeRule;
import za.co.handyflow.platform.debtcollection.domain.model.FeeRuleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FeeRuleResponse(
        UUID id, String code, int ruleVersion, String description, FeeCalcMethod calcMethod, BigDecimal fixedAmount,
        BigDecimal percentOfOutstanding, BigDecimal maxPerCase, Integer maxChargesPerMonth, LocalDate effectiveFrom,
        LocalDate effectiveTo, String legalBasisNote, FeeRuleStatus status, String approvedByName, Instant approvedAt,
        String retiredReason, Instant createdAt
) {
    public static FeeRuleResponse of(FeeRule r) {
        return new FeeRuleResponse(r.getId(), r.getCode(), r.getRuleVersion(), r.getDescription(), r.getCalcMethod(),
                r.getFixedAmount(), r.getPercentOfOutstanding(), r.getMaxPerCase(), r.getMaxChargesPerMonth(),
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getLegalBasisNote(), r.getStatus(), r.getApprovedByName(),
                r.getApprovedAt(), r.getRetiredReason(), r.getCreatedAt());
    }
}
