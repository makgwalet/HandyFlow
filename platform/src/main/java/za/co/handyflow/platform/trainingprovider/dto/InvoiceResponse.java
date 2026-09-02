package za.co.handyflow.platform.trainingprovider.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID clientId,
        String invoiceNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate issueDate,
        LocalDate dueDate,
        int delegateCount,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balance,
        String status,
        Instant createdAt
) {}
