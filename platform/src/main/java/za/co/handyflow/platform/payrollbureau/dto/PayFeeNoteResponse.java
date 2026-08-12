package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayFeeNoteResponse(
        UUID id, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate,
        BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total, BigDecimal amountPaid,
        BigDecimal balance, String status, Instant sentAt, Instant paidAt
) {}