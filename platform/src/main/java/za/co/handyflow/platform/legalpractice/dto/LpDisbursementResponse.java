package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpDisbursementResponse(
        UUID id,
        UUID matterId,
        LocalDate disbursementDate,
        String description,
        BigDecimal amount,
        boolean paidFromTrust,
        String status,
        UUID invoiceId,
        Instant createdAt,
        Instant updatedAt
) {}
