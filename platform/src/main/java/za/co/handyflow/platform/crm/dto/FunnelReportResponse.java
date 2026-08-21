package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.LeadStage;

import java.math.BigDecimal;
import java.util.List;

/**
 * FIX: backlog 4.3 — "no conversion-rate/funnel reporting." Reporting-
 * layer only, per the backlog's own scoping — no data-model change, this
 * is entirely computed from CustomerActivity's existing STAGE_CHANGED
 * events plus each lead's own createdAt (see CrmReportingService's
 * Javadoc for why NEW itself isn't a logged activity and has to be
 * handled as the implicit starting stage).
 */
public record FunnelReportResponse(
        List<StageFunnelEntry> stages,
        long totalLeads,
        long totalWon,
        long totalLost,
        long totalStillOpen,
        BigDecimal overallConversionRate   // totalWon / totalLeads, null if totalLeads == 0
) {
    public record StageFunnelEntry(
            LeadStage stage,
            long reachedCount,
            BigDecimal conversionFromPreviousStage,   // null for NEW (no "previous" stage)
            Double avgDaysInStage                      // null if no lead has yet moved on from this stage
    ) {}
}