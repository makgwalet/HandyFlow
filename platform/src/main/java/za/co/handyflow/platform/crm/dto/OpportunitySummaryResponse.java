package za.co.handyflow.platform.crm.dto;

import java.math.BigDecimal;
import java.util.List;
import za.co.handyflow.platform.crm.domain.model.OpportunityStage;

public record OpportunitySummaryResponse(
        int openCount, BigDecimal openValue, BigDecimal weightedValue, int overdueCount,
        int wonCount, BigDecimal wonValue, int lostCount, List<StageTotal> byStage
) {
    public record StageTotal(OpportunityStage stage, int count, BigDecimal value) {}
}
