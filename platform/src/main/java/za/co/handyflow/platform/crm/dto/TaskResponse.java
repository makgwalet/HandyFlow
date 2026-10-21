package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the CRM task queue: a follow-up with its customer's name. */
public record TaskResponse(
        UUID id,
        UUID customerId,
        String customerName,
        LocalDate dueDate,
        String note,
        UUID assignedTo,
        String priority,        // LOW, NORMAL or HIGH
        boolean completed,
        Instant completedAt,
        String outcome,         // null until finished
        boolean overdue,
        long daysOverdue        // 0 unless overdue
) {}
