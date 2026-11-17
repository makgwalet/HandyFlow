package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * scopeType: DEPARTMENT | COST_CENTRE | PROJECT — scopeValue is matched (case-insensitively) against the
 * department / costCentre / projectRef on requisitions and POs.
 * controlMode: NONE (track only) | WARN | HARD_STOP.
 */
public record CreateBudgetRequest(
        String name,
        String scopeType,
        String scopeValue,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        String controlMode,
        String currency
) {}
