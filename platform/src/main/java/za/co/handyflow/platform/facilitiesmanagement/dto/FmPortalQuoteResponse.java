package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the client sees: no internal note and no staff identities. {@code canRespond}: it can be accepted or declined now.
 * {@code decisionNote} is the client's own comment, or staff's reason when the quote was withdrawn.
 */
public record FmPortalQuoteResponse(
        UUID id, String quoteNumber, UUID siteId, String title, String description, String status, boolean canRespond,
        LocalDate validUntil, BigDecimal subtotal, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal total,
        Instant sentAt, Instant decidedAt, String decisionNote, List<Line> lines
) {
    public record Line(int lineNo, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
