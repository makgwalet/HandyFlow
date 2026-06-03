package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        UUID clientId,
        UUID practitionerId,
        LocalDate entryDate,
        String activityType,
        String description,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal lineTotal,
        boolean billable,
        String status,
        UUID invoiceId,
        Instant createdAt
) {
}
