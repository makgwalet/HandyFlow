package za.co.handyflow.platform.bookingagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookAgencyInvoiceResponse(
        UUID id, String invoiceNumber, String description, LocalDate periodStart, LocalDate periodEnd,
        LocalDate invoiceDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount,
        BigDecimal total, BigDecimal amountPaid, BigDecimal balance, String status,
        Instant sentAt, Instant paidAt
) {}