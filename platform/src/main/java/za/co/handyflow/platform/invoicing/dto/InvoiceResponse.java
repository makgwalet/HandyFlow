package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        String status,
        UUID customerId,
        UUID quoteId,
        String title,
        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        BigDecimal amountPaid,
        String currency,
        LocalDate dueDate,
        Instant issuedAt,
        List<LineItemResponse> lineItems,
        Instant createdAt
) {}