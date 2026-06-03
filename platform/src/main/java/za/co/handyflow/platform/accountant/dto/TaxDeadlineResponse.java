package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxDeadlineResponse(
        UUID id,
        UUID clientId,
        String clientName,               // denormalised for portfolio views
        String deadlineType,
        int periodYear,
        Integer periodMonth,
        LocalDate statutoryDueDate,
        LocalDate adjustedDueDate,
        String status,
        LocalDate filedDate,
        String sarsReference,
        BigDecimal filingAmount,
        int daysUntilDue,                // negative = overdue
        String notes
) {
}
