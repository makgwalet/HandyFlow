package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;

public record ApSummaryResponse(
        BigDecimal totalOutstanding,
        BigDecimal overdueAmount,
        BigDecimal dueThisWeek,
        BigDecimal dueThisMonth,
        long       draftCount,
        long       approvedCount,
        long       overdueCount,
        long       pendingBatches
) {}
