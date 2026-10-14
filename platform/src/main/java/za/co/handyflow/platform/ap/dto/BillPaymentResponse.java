package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillPaymentResponse(
        UUID id, BigDecimal amount, LocalDate paidOn, String reference, UUID batchId,
        UUID journalEntryId, UUID paidBy, Instant createdAt
) {}
