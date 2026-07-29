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
        Instant    createdAt
) {}