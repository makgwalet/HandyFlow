package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Deliberately narrow — this is what an unauthenticated client sees via the
 * public accept/reject link. No internal notes, no soft-delete state, no
 * tenant-internal identifiers beyond what's needed to render a quote and
 * act on it.
 */
public record PublicQuoteView(
        String quoteNumber,
        String status,
        String companyName,
        String title,
        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        String currency,
        Instant expiresAt,
        List<LineItemResponse> lineItems,
        boolean actionable   // false once expired/already actioned — frontend disables the buttons
) {}