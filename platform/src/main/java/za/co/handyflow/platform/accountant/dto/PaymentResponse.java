package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID feeNoteId,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String reference,
        String notes,
        String recordedByName,
        Instant createdAt
) {
}