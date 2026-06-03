package za.co.handyflow.platform.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Z-Report: end-of-day summary per cash session (or date range if no session).
 * Breaks down sales by payment method and VAT.
 */
public record ZReportResponse(
        UUID       sessionId,
        String     sessionNumber,
        LocalDate  reportDate,
        String     openedByName,
        String     closedByName,
        Instant    openedAt,
        Instant    closedAt,

        // Totals
        BigDecimal grossSales,          // subtotal before VAT
        BigDecimal totalVat,
        BigDecimal totalDiscount,
        BigDecimal netSales,            // total_amount (VAT-inclusive)
        int        transactionCount,
        int        refundCount,
        BigDecimal totalRefunds,

        // Cash reconciliation
        BigDecimal openingFloat,
        BigDecimal expectedCash,        // openingFloat + cash sales - cash refunds
        BigDecimal closingFloat,
        BigDecimal cashVariance,

        // Payment method breakdown
        List<PaymentMethodBreakdown> byPaymentMethod,

        // Top items sold
        List<TopItem> topItems
) {
    public record PaymentMethodBreakdown(
            String     paymentMethod,
            int        count,
            BigDecimal totalAmount
    ) {}

    public record TopItem(
            String     itemName,
            BigDecimal qtySold,
            BigDecimal totalRevenue
    ) {}
}
