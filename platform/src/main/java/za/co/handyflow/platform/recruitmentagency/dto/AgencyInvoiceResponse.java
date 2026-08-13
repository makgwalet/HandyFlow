package za.co.handyflow.platform.recruitmentagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AgencyInvoiceResponse(
        UUID id, String invoiceNumber, String description, LocalDate invoiceDate, LocalDate dueDate,
        BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total, BigDecimal amountPaid,
        BigDecimal balance, String status, Instant sentAt, Instant paidAt
) {}