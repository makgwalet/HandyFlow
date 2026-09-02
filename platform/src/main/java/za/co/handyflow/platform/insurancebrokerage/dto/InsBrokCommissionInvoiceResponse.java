package za.co.handyflow.platform.insurancebrokerage.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InsBrokCommissionInvoiceResponse(
        UUID id,
        UUID clientId,
        UUID policyId,
        String invoiceNumber,
        String description,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balance,
        String status,
        Instant sentAt,
        Instant paidAt
) {}
