package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpInvoiceResponse(
        UUID id,
        UUID clientId,
        UUID matterId,
        String invoiceNumber,
        String description,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal outstandingBalance,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
