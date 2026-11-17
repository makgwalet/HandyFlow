package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** hardStop = would be blocked (unless an authorised override covers it). */
public record BudgetCheckResponse(
        boolean hardStop,
        boolean hasWarnings,
        List<Finding> findings
) {
    public record Finding(UUID budgetId, String budgetName, String scopeType, String scopeValue, String controlMode,
                          BigDecimal amount, BigDecimal used, BigDecimal requested, BigDecimal availableAfter,
                          String state, String message) {}
}
