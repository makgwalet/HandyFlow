package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Live operational picture, computed on request within the caller's access scope. {@code finance} is null for
 * callers without billing/cost authority and for field technicians.
 * <ul>
 *   <li><b>slaAtRisk / slaBreached</b>: open work orders whose response or resolution target is currently
 *       at risk / already missed (the same states the work-order API shows).</li>
 *   <li><b>overdue</b>: past the scheduled date and not finished or awaiting a verifier.</li>
 *   <li>The 30-day figures cover work COMPLETED in the last 30 days; resolution compliance is met ÷ (met + breached)
 *       over those jobs, null when none carried an SLA.</li>
 * </ul>
 */
public record FmDashboardResponse(Instant generatedAt, WorkOrderKpis workOrders, FinanceKpis finance) {

    public record WorkOrderKpis(long open, long unassigned, long onHold, long overdue, long awaitingApproval,
                                long awaitingVerification, long slaAtRisk, long slaBreached,
                                Map<String, Long> openByStatus, Map<String, Long> openByPriority,
                                long completedLast30Days, BigDecimal avgResolutionHoursLast30Days,
                                BigDecimal resolutionSlaComplianceLast30Days) {}

    /**
     * Receivables are SENT/PARTIAL invoices less payments and credits; overdue means past due date. "Invoiced this
     * month" is the total (incl. VAT) of approved-or-later invoices issued in the current South African month.
     */
    public record FinanceKpis(BigDecimal outstandingReceivables, BigDecimal overdueReceivables, long overdueInvoices,
                              BigDecimal invoicedThisMonth, long draftInvoices, long approvedNotSent,
                              long creditNotesAwaitingApproval, BigDecimal unbilledCompletedWork) {}
}
