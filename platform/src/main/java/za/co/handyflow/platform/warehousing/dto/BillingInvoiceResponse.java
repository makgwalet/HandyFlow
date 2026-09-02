package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillingInvoiceResponse(
        UUID id, UUID clientId, String invoiceNumber, LocalDate periodStart, LocalDate periodEnd,
        LocalDate invoiceDate, LocalDate dueDate, BigDecimal storageFee, BigDecimal handlingFee,
        BigDecimal vatAmount, BigDecimal subtotal, BigDecimal total, BigDecimal amountPaid, BigDecimal balance,
        String status, Instant sentAt, Instant paidAt
) {}
