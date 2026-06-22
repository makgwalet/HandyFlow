package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID   id,
        String invoiceNumber,
        String status,
        UUID   customerId,
        UUID   quoteId,
        String title,

        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        BigDecimal amountPaid,

        String    currency,
        LocalDate dueDate,
        Instant   issuedAt,

        List<LineItemResponse> lineItems,
        Instant createdAt,

        // ── Type / recurring ────────────────────────────────────────────────
        /** STANDARD | RECURRING_INSTANCE | RETAINER */
        String invoiceType,

        /** Non-null for RECURRING_INSTANCE invoices. */
        UUID recurringScheduleId,

        // ── Retainer / upfront-hours ────────────────────────────────────────
        /** Non-null for RETAINER invoices. */
        BigDecimal committedHours,
        BigDecimal ratePerHour,
        BigDecimal hoursConsumed,
        BigDecimal creditAmount,

        // ── Walk-in (mirrors QuoteResponse) ────────────────────────────────
        String walkinClientName,
        String walkinClientEmail,
        String walkinClientPhone
) {
    /** Convenience: how many hours remain before the retainer is exhausted. */
    public BigDecimal remainingHours() {
        if (committedHours == null || hoursConsumed == null) return null;
        var remaining = committedHours.subtract(hoursConsumed);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    public boolean isOverage() {
        return committedHours != null && hoursConsumed != null
                && hoursConsumed.compareTo(committedHours) > 0;
    }
}
