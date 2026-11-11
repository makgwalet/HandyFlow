package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Planned vs committed vs actual for one event.
 * <ul>
 *   <li>planned     — sum of the budget lines</li>
 *   <li>committed   — approved (and completed) supplier commitments</li>
 *   <li>invoiced    — active invoices recorded (the cost actually incurred)</li>
 *   <li>paid        — active payments recorded, deposits included</li>
 *   <li>forecast    — expected final cost: for each commitment the larger of what was
 *                     committed and what was invoiced (just what was invoiced once it is
 *                     COMPLETED), plus any invoices that have no commitment</li>
 *   <li>variance    — planned minus forecast; negative means over budget</li>
 *   <li>outstanding — forecast minus paid, never below zero</li>
 * </ul>
 */
public record BudgetSummaryResponse(
        String budgetStatus,
        BigDecimal planned,
        BigDecimal committed,
        BigDecimal invoiced,
        BigDecimal paid,
        BigDecimal forecast,
        BigDecimal variance,
        BigDecimal outstanding,
        BigDecimal costPerRegistered,       // forecast / registered guests; null when nobody is registered
        BigDecimal costPerCheckedIn,        // forecast / checked-in guests; null until someone has checked in
        List<LineSummary> lines,
        List<BudgetAlert> alerts
) {
    public record LineSummary(
            UUID id,
            String category,
            String description,
            BigDecimal planned,
            BigDecimal committed,
            BigDecimal invoiced,
            BigDecimal paid,
            BigDecimal forecast,
            BigDecimal variance,
            boolean overBudget
    ) {}

    /** type: OVER_BUDGET | OVER_COMMITMENT | DEPOSIT_DUE | DELIVERABLE_OVERDUE | PAID_ON_CANCELLED | UNRECONCILED */
    public record BudgetAlert(String type, String severity, String message, UUID commitmentId) {}
}