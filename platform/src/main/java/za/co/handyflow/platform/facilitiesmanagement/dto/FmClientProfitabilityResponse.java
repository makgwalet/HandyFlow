package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Client profitability for a period. Definitions (every figure comes from approved billing and completed
 * work records):
 * <ul>
 *   <li>{@code invoicedRevenue} — subtotal (excluding VAT) of APPROVED-or-later, non-cancelled invoices issued in the period;</li>
 *   <li>{@code internalCost} — internal cost of work orders COMPLETED in the period, whether or not they have been billed
 *       (so retainer clients carry the cost of the work done for them);</li>
 *   <li>{@code grossMargin} = invoicedRevenue - internalCost; {@code marginPercent} = margin / revenue (null when revenue is zero);</li>
 *   <li>{@code unbilledBillableValue} — charge on completed, not-yet-invoiced work (all time, not just the period) awaiting an invoice.</li>
 * </ul>
 * Work orders completed without itemised cost lines assume internal cost = charge (no markup information).
 */
public record FmClientProfitabilityResponse(
        UUID clientId, LocalDate from, LocalDate to, BigDecimal invoicedRevenue, BigDecimal internalCost,
        BigDecimal grossMargin, BigDecimal marginPercent, BigDecimal unbilledBillableValue, long completedWorkOrders
) {}