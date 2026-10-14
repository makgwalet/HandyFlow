package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditNoteResponse(
        UUID id,
        String creditNoteNumber,
        UUID invoiceId,
        String invoiceNumber,
        String reason,
        String description,
        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        String currency,
        Instant issuedAt,
        Instant createdAt,
        // Finance patch F9
        String status,
        UUID createdBy,
        Instant decidedAt,
        String rejectionReason
) {}