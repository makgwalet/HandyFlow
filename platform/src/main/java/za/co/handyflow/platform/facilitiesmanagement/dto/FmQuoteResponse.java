package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Staff view. {@code status} shows EXPIRED for a SENT quote past its validity even before the daily sweep records it. */
public record FmQuoteResponse(
        UUID id, String quoteNumber, UUID clientId, UUID siteId, UUID assetId, UUID workOrderId, String title, String description,
        String status, LocalDate validUntil, BigDecimal subtotal, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal total,
        String internalNote, Instant sentAt, Instant decidedAt, String decisionNote, Instant createdAt, List<Line> lines
) {
    public record Line(int lineNo, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
