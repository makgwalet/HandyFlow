// fuel/dto/FuelMarginReportResponse.java

package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// FIX (fuel cost/margin engine, agreed design): two views bundled in one
// report per the agreed design — external margin (revenue-generating
// deliveries + customer-billed dispatches) and internal fuel cost
// (non-billed dispatches, tracked for cost visibility even though
// there's no sale/margin on them).
public record FuelMarginReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal totalRevenue,
        BigDecimal totalCostOfRevenueGenerating,
        BigDecimal totalMargin,
        BigDecimal marginPercent,        // null if totalRevenue is zero
        BigDecimal totalInternalLitres,
        BigDecimal totalInternalCost,
        int transactionsWithoutCostData, // predates this feature, or tank had no WAC yet — flagged so the report is honest about incomplete coverage rather than silently under-reporting
        List<FuelMarginLineResponse> lines
) {}
