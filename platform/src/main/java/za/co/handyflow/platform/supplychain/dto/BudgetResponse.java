package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * committed = sum of non-cancelled approved POs; actual = approved/paid invoices against them;
 * used = per PO the larger of the two (so an invoice above its PO counts at the invoiced amount);
 * available = amount - used. state: OK | NEAR_LIMIT | EXCEEDED.
 * For a budget with sub-budgets, committed/actual/used roll up the sub-budgets (each purchase order counted once);
 * allocated = the active children's amounts, unallocated = amount - allocated.
 */
public record BudgetResponse(
        UUID id,
        String name,
        String scopeType,
        String scopeValue,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        String controlMode,
        String status,
        String currency,
        BigDecimal committed,
        BigDecimal actual,
        BigDecimal used,
        BigDecimal available,
        double utilisationPct,
        String state,
        UUID parentBudgetId,
        BigDecimal allocated,
        BigDecimal unallocated,
        int childCount
) {}
