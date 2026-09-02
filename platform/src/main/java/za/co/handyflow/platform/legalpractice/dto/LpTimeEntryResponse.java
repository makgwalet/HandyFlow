package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpTimeEntryResponse(
        UUID id,
        UUID matterId,
        UUID attorneyId,
        LocalDate entryDate,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal lineTotal,
        String description,
        boolean billable,
        String status,
        UUID invoiceId,
        Instant createdAt,
        Instant updatedAt
) {}
