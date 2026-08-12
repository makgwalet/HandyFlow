package za.co.handyflow.platform.accountant.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PortalTaxDeadlineResponse(
        UUID id,
        String deadlineType,     // raw code, e.g. "VAT201" — kept for any
        // future filtering/grouping the frontend wants
        String friendlyLabel,    // e.g. "VAT return" — same mapping as
        // EmailTemplates.clientDeadlineReminder()
        LocalDate dueDate,
        String status,           // PENDING | FILED | OVERDUE
        int daysUntilDue,        // negative = overdue
        LocalDate filedDate
) {}
