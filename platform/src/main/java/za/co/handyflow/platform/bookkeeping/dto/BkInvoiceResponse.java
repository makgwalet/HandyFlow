package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BkInvoiceResponse(
        UUID id, UUID clientId, String invoiceNumber, LocalDate periodStart, LocalDate periodEnd,
        LocalDate issueDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total,
        BigDecimal amountPaid, BigDecimal balance, String status, Instant createdAt
) {}
