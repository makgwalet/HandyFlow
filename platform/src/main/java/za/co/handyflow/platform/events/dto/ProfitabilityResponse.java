package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;

/**
 * Revenue against cost for one event.
 * <ul>
 *   <li>grossRevenue  — money received through checkout, plus amounts staff typed in for tickets
 *                       registered without a payment record</li>
 *   <li>refunds       — money actually handed back</li>
 *   <li>fees          — provider fees reported on received payments</li>
 *   <li>netRevenue    — gross - refunds - fees</li>
 *   <li>forecastProfit / actualProfit — net revenue minus forecast cost / minus cost invoiced so far</li>
 * </ul>
 * Revenue comes from payments recorded in Events; costs are Events' own tracking. Neither is the
 * accounting ledger, and VAT is not separated out.
 */
public record ProfitabilityResponse(
        BigDecimal grossRevenue,
        BigDecimal receivedViaCheckout,
        BigDecimal staffRecordedRevenue,        // unverified: typed in by staff, no payment record behind it
        BigDecimal refunds,
        BigDecimal fees,
        BigDecimal netRevenue,
        BigDecimal pendingRevenue,              // checkouts still inside their hold — expected, not received
        BigDecimal refundsOwed,                 // received but flagged REQUIRES_REFUND and not yet returned
        BigDecimal costForecast,
        BigDecimal costInvoiced,
        BigDecimal forecastProfit,
        BigDecimal actualProfit,
        BigDecimal marginPercent,               // forecastProfit / netRevenue; null when there is no net revenue
        BigDecimal revenuePerAttendee,          // netRevenue / checked-in guests; null until someone checks in
        BigDecimal costPerAttendee,
        String budgetStatus
) {}