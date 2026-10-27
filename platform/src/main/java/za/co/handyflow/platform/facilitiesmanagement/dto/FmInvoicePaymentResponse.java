package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FmInvoicePaymentResponse(
        UUID id, UUID invoiceId, BigDecimal amount, LocalDate paymentDate, String method, String reference,
        UUID recordedByUserId, Instant createdAt, String glStatus
) {}