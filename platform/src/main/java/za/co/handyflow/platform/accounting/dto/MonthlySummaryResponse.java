package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;

public record MonthlySummaryResponse(
        int    year,
        int    month,       // 1-12
        String monthLabel,  // "Jan", "Feb", etc.
        BigDecimal revenue,
        BigDecimal expenses,
        BigDecimal netProfit
) {}