package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;

public record BudgetSummaryResponse(
        BigDecimal  totalBudget,
        BigDecimal  totalCommitted,
        BigDecimal  totalActual,
        BigDecimal  totalVariance,
        BigDecimal  completionPct,   // budget spent %
        // EVM fields
        BigDecimal  plannedValue,    // PV — budgeted cost of work scheduled
        BigDecimal  earnedValue,     // EV — budgeted cost of work performed
        BigDecimal  actualCost,      // AC
        BigDecimal  spi,             // Schedule Performance Index = EV/PV
        BigDecimal  cpi,             // Cost Performance Index = EV/AC
        BigDecimal  eac,             // Estimate At Completion = BAC/CPI
        BigDecimal  etc              // Estimate To Complete = EAC - AC
) {}
