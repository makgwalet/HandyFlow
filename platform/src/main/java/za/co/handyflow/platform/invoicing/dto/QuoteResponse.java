package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteResponse(
        UUID id,
        String quoteNumber,
        String status,
        UUID customerId,
        String title,
        String notes,
        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        String currency,
        Instant sentAt,
        Instant expiresAt,
        Instant acceptedAt,
        List<LineItemResponse> lineItems,
        Instant createdAt,

        // Walk-in fields — null when customerId is set
        String walkinClientName,
        String walkinClientEmail,
        String walkinClientPhone,

        // FIX: "no quote view-tracking" gap
        Instant firstViewedAt,
        Instant lastViewedAt,
        int viewCount
) {}