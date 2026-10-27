package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** STAFF-facing credit note. */
public record FmCreditNoteResponse(
        UUID id, String creditNoteNumber, UUID invoiceId, String invoiceNumber, UUID clientId, LocalDate issueDate,
        String reason, BigDecimal subtotal, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal total, String status,
        UUID preparedByUserId, UUID approvedByUserId, Instant approvedAt, String glStatus, String glLastError,
        Instant createdAt
) {}
