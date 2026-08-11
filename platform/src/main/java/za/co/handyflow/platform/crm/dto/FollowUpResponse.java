package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FollowUpResponse(
        UUID       id,
        UUID       customerId,
        LocalDate  dueDate,
        String     note,
        UUID       assignedTo,
        boolean    completed,
        Instant    completedAt,
        boolean    overdue,
        Instant    createdAt,
        String     outcome,            // null until completed — COMPLETED / NO_RESPONSE / RESCHEDULED
        UUID       rescheduledFromId   // set only if this record exists because an earlier one was rescheduled
) {}